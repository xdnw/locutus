package link.locutus.discord.db.entities.sheet;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CustomSheet;
import link.locutus.discord.db.entities.SelectionAlias;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class CustomSheetManager {
    private final GuildDB db;
    private Map<Class, Map<String, SelectionAlias>> customSelections = null;

    public CustomSheetManager(GuildDB db) {
        this.db = db;
        createTables();
    }

    public void createTables() {
        db.executeStmt("CREATE TABLE IF NOT EXISTS `SELECTION_ALIAS` (`name` VARCHAR PRIMARY KEY COLLATE NOCASE, `type` VARCHAR NOT NULL COLLATE NOCASE, `selection` VARCHAR NOT NULL)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS `SHEET_TEMPLATE` (`name` VARCHAR PRIMARY KEY, `type` VARCHAR NOT NULL, `columns` VARCHAR NOT NULL)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS `CUSTOM_SHEET` (`name` VARCHAR PRIMARY KEY, url VARCHAR NOT NULL)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS `CUSTOM_SHEET_TABS` (`sheet` VARCHAR NOT NULL, `tab` VARCHAR NOT NULL, `selector` VARCHAR NOT NULL, `template` VARCHAR NOT NULL, PRIMARY KEY (`sheet`, `tab`))");
    }

    public Set<String> getSheetTemplateNames(boolean addPrefix) {
        Set<String> names = new HashSet<>();
        db.query("SELECT `type`, `name` FROM `SHEET_TEMPLATE`", stmt -> {}, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                String nameFull = rs.getString("name");
                if (addPrefix) {
                    String type = rs.getString("type");
                    nameFull = PlaceholdersMap.getClassName(type) + ":" + nameFull;
                }
                names.add(nameFull);
            }
        });
        return names;
    }

    public Map<String, SheetTemplate> getSheetTemplates(Class type) {
        Map<String, SheetTemplate> sheets = new LinkedHashMap<>();
        db.query("SELECT * FROM `SHEET_TEMPLATE` WHERE type = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, PlaceholdersMap.getClassName(type));
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                SheetTemplate sheet = new SheetTemplate(rs);
                sheets.put(sheet.getName(), sheet);
            }
        });
        return sheets;
    }

    public Map<String, SheetTemplate> getSheetTemplates(List<String> errors) {
        Map<String, SheetTemplate> sheets = new LinkedHashMap<>();
        db.query("SELECT * FROM `SHEET_TEMPLATE`", stmt -> {}, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                try {
                    SheetTemplate sheet = new SheetTemplate(rs);
                    sheets.put(sheet.getName(), sheet);
                } catch (IllegalArgumentException e) {
                    errors.add(e.getMessage());
                }
            }
        });
        return sheets;
    }

    public SheetTemplate getSheetTemplate(String name) {
        if (name.contains(":")) name = name.substring(name.indexOf(":") + 1);
        AtomicReference<SheetTemplate> sheet = new AtomicReference<>();
        String finalName = name;
        db.query("SELECT * FROM `SHEET_TEMPLATE` WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, finalName);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                sheet.set(new SheetTemplate(rs));
            }
        });
        return sheet.get();
    }

    public void addSheetTemplate(SheetTemplate sheet) {
        String query = "CREATE TABLE IF NOT EXISTS `SHEET_TEMPLATE` (`name` VARCHAR PRIMARY KEY, `type` VARCHAR NOT NULL, `columns` VARCHAR NOT NULL)";
        db.update("INSERT INTO `SHEET_TEMPLATE`(`name`, `type`, `columns`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, sheet.getName());
            stmt.setString(2, PlaceholdersMap.getClassName(sheet.getType()));
            stmt.setString(3, StringMan.join(sheet.getColumns(), "\n"));
        });
    }

    public void deleteSheetTemplate(String name) {
        if (name.contains(":")) name = name.substring(name.indexOf(":") + 1);
        String finalName = name;
        db.update("DELETE FROM `SHEET_TEMPLATE` WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, finalName);
        });
    }

    public void renameSheetTemplate(SheetTemplate sheet, String name) {
        db.update("UPDATE `SHEET_TEMPLATE` SET name = ? WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setString(2, sheet.getName());
        });
        sheet.name = name;
    }

    public void renameSelectionAlias(SelectionAlias selectionAlias, String name) {
        db.update("UPDATE `SELECTION_ALIAS` SET name = ? WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setString(2, selectionAlias.getName());
        });
        customSelections.get(selectionAlias.getType()).remove(selectionAlias.getName().toLowerCase(Locale.ROOT));
        selectionAlias.setName(name);
        customSelections.get(selectionAlias.getType()).put(name.toLowerCase(Locale.ROOT), selectionAlias);
    }

    public Map<Class, Map<String, SelectionAlias>> getSelectionAliases() {
        if (customSelections == null) {
            synchronized (this) {
                if (customSelections == null) {
                    customSelections = new ConcurrentHashMap<>();
                    db.query("SELECT * from SELECTION_ALIAS", stmt -> {}, (ThrowingConsumer<ResultSet>) rs -> {
                        while (rs.next()) {
                            String name = rs.getString("name");
                            String typeStr = rs.getString("type");
                            Class typeFinal = null;
                            for (Class<?> type : Locutus.cmd().getV2().getPlaceholders().getTypes()) {
                                if (PlaceholdersMap.getClassName(type).equalsIgnoreCase(typeStr)) {
                                    typeFinal = type;
                                    break;
                                }
                            }
                            if (typeFinal == null) {
                                return;
                            }
                            String selection = rs.getString("selection");
                            SelectionAlias alias = new SelectionAlias(name, typeFinal, selection);
                            customSelections.computeIfAbsent(typeFinal, t -> new ConcurrentHashMap<>()).put(name.toLowerCase(Locale.ROOT), alias);
                        }
                    });
                }
            }
        }
        return customSelections;
    }

    public <T> Map<String, SelectionAlias<T>> getSelectionAliases(Class<T> type) {
        Map<String, SelectionAlias> result = getSelectionAliases().getOrDefault(type, new HashMap<>());
        return (Map<String, SelectionAlias<T>>) (Map) result;
    }

    public <T> SelectionAlias<T> getSelectionAlias(String name, Class<T> type) {
        if (name.contains(":")) name = name.substring(name.indexOf(":") + 1);
        name = name.toLowerCase(Locale.ROOT);
        Map<String, SelectionAlias> selections = getSelectionAliases().get(type);
        if (selections == null) {
            return null;
        }
        SelectionAlias selection = selections.get(name);
        if (selection == null) {
            return null;
        }
        return selection;
    }

    public Set<String> getSelectionAliasNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<Class, Map<String, SelectionAlias>> entry : getSelectionAliases().entrySet()) {
            String prefix = PlaceholdersMap.getClassName(entry.getKey()) + ":";
            for (String name : entry.getValue().keySet()) {
                names.add(prefix + name);
            }
        }
        return names;
    }

    public <T> SelectionAlias<T> getSelectionAlias(String name) {
        Predicate<Class> typePrefix = f -> true;
        if (name.contains(":")) {
            String[] split = name.split(":", 2);
            name = split[1];
            String prefix = split[0];
            typePrefix = f -> PlaceholdersMap.getClassName(f).equalsIgnoreCase(prefix);
        }
        for (Map.Entry<Class, Map<String, SelectionAlias>> entry : getSelectionAliases().entrySet()) {
            if (!typePrefix.test(entry.getKey())) {
                continue;
            }
            Map<String, SelectionAlias> selections = entry.getValue();
            Class type = entry.getKey();
            SelectionAlias selection = selections.get(name);
            if (selection != null) {
                return selection;
            }
        }
        return null;
    }

    public void removeSelectionAlias(String name) {
        name = name.toLowerCase(Locale.ROOT);
        String finalName = name;
        getSelectionAliases().values().forEach(map -> map.remove(finalName.toLowerCase(Locale.ROOT)));
        db.update("DELETE FROM SELECTION_ALIAS WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, finalName);
        });
    }

    public SelectionAlias addSelectionAlias(String name, Class type, String selection) {
        name = name.toLowerCase(Locale.ROOT);
        String finalName = name;
        db.update("INSERT INTO SELECTION_ALIAS(name, type, selection) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, finalName);
            stmt.setString(2, PlaceholdersMap.getClassName(type));
            stmt.setString(3, selection);
        });
        SelectionAlias alias = new SelectionAlias(name, type, selection);
        getSelectionAliases().computeIfAbsent(type, t -> new ConcurrentHashMap<>()).put(name, alias);
        return alias;
    }

    // Add methods for custom sheets

    public void addCustomSheet(String name, String url) {
        db.update("INSERT INTO CUSTOM_SHEET(name, url) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setString(2, url);
        });
    }

    public void deleteCustomSheet(String name) {
        db.update("DELETE FROM CUSTOM_SHEET WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
        });
    }

    public void renameCustomSheet(String name, String newName) {
        db.update("UPDATE CUSTOM_SHEET SET name = ? WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, newName);
            stmt.setString(2, name);
        });
    }

    public void addCustomSheetTab(String sheet, String tab, String selector, String template) {
        db.update("INSERT INTO CUSTOM_SHEET_TABS(sheet, tab, selector, template) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, sheet);
            stmt.setString(2, tab.toLowerCase(Locale.ROOT));
            stmt.setString(3, selector);
            stmt.setString(4, template);
        });
    }

    public void deleteCustomSheetTab(String sheet, String tab) {
        db.update("DELETE FROM CUSTOM_SHEET_TABS WHERE sheet = ? AND tab = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, sheet);
            stmt.setString(2, tab);
        });
    }

    public void renameCustomSheetTab(String sheet, String name, String newName) {
        db.update("UPDATE CUSTOM_SHEET_TABS SET name = ? WHERE sheet = ? AND tab = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, newName);
            stmt.setString(2, sheet);
            stmt.setString(3, name);
        });
    }

    /**
     * @return Map of name -> url
     */
    public Map<String, String> getCustomSheets() {
        Map<String, String> sheets = new HashMap<>();
        db.query("SELECT `name`, `url` FROM `CUSTOM_SHEET`", stmt -> {}, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                sheets.put(rs.getString("name"), rs.getString("url"));
            }
        });
        return sheets;
    }

    public String getCustomSheetUrl(String name) {
        AtomicReference<String> url = new AtomicReference<>();
        db.query("SELECT `url` FROM `CUSTOM_SHEET` WHERE `name` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                url.set(rs.getString("url"));
            }
        });
        return url.get();
    }

    public String getCustomSheetName(String url) {
        AtomicReference<String> name = new AtomicReference<>();
        db.query("SELECT `name` FROM `CUSTOM_SHEET` WHERE `url` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, url);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                name.set(rs.getString("name"));
            }
        });
        return name.get();
    }

    public CustomSheet getCustomSheetById(String url) {
        String name = getCustomSheetName(url);
        if (name == null) {
            return null;
        }
        return selectCustomSheets(name, url, "WHERE `name` = ?", stmt -> {
            stmt.setString(1, name);
        });
    }

    public CustomSheet getCustomSheet(String name) {
        String url = getCustomSheetUrl(name);
        if (url == null) {
            return null;
        }
        return selectCustomSheets(name, url, "WHERE `name` = ?", stmt -> {
            stmt.setString(1, name);
        });
    }

    private CustomSheet selectCustomSheets(String name, String url, String query, ThrowingConsumer<PreparedStatement> condition) {
        Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs = new LinkedHashMap<>();
        db.query("SELECT * FROM `CUSTOM_SHEET_TABS` " + query, condition, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                String tabName = rs.getString("tab");
                String selector = rs.getString("selector");
                String template = rs.getString("template");
                SelectionAlias selectionAlias = getSelectionAlias(selector);
                SheetTemplate sheetTemplate = getSheetTemplate(template);
                tabs.put(tabName, new AbstractMap.SimpleEntry<>(selectionAlias, sheetTemplate));
            }
        });
        return new CustomSheet(name, url, tabs);
    }
}
