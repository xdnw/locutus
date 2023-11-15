package link.locutus.discord.db.entities.sheet;

import link.locutus.discord.Locutus;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class CustomSheetManager {
    private final GuildDB db;

    public CustomSheetManager(GuildDB db) {
        this.db = db;
        createTables();
    }

    public void createTables() {
        db.executeStmt("CREATE TABLE IF NOT EXISTS `SELECTION_ALIAS` (`name` VARCHAR PRIMARY KEY, `type` VARCHAR NOT NULL, `selection` VARCHAR NOT NULL)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS `SHEET_TEMPLATE` (`name` VARCHAR PRIMARY KEY, `type` VARCHAR NOT NULL, `columns` VARCHAR NOT NULL)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS `CUSTOM_SHEET` (`name` VARCHAR PRIMARY KEY, url VARCHAR NOT NULL)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS `CUSTOM_SHEET_TABS` (`sheet` VARCHAR NOT NULL, `tab` VARCHAR NOT NULL, `selector` VARCHAR NOT NULL, `template` VARCHAR NOT NULL, PRIMARY KEY (`sheet`, `tab`))");
    }

    public Set<String> getSheetTemplateNames() {
        Set<String> names = new HashSet<>();
        db.query("SELECT `name` FROM `SHEET_TEMPLATE`", stmt -> {}, (ThrowingConsumer<ResultSet>) rs -> {
            names.add(rs.getString("name"));
        });
        return names;
    }

    public Map<String, SheetTemplate> getSheetTemplates(List<String> errors) {
        Map<String, SheetTemplate> sheets = new LinkedHashMap<>();
        db.query("SELECT * FROM `SHEET_TEMPLATE`", stmt -> {}, (ThrowingConsumer<ResultSet>) rs -> {
            try {
                SheetTemplate sheet = new SheetTemplate(rs);
                sheets.put(sheet.getName(), sheet);
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        });
        return sheets;
    }

    public SheetTemplate getSheetTemplate(String name) {
        AtomicReference<SheetTemplate> sheet = new AtomicReference<>();
        db.query("SELECT * FROM `SHEET_TEMPLATE` WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            sheet.set(new SheetTemplate(rs));
        });
        return sheet.get();
    }

    public void addSheetTemplate(SheetTemplate sheet) {
        String query = "CREATE TABLE IF NOT EXISTS `SHEET_TEMPLATE` (`name` VARCHAR PRIMARY KEY, `type` VARCHAR NOT NULL, `columns` VARCHAR NOT NULL)";
        db.update("INSERT INTO `SHEET_TEMPLATE`(`name`, `type`, `selection`, `columns`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, sheet.getName());
            stmt.setString(2, sheet.getType().getSimpleName());
            stmt.setString(4, StringMan.join(sheet.getColumns(), "\n"));
        });
    }

    public void deleteSheetTemplate(String name) {
        db.update("DELETE FROM `SHEET_TEMPLATE` WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
        });
    }

    public void renameSheetTemplate(SheetTemplate sheet, String name) {
        db.update("UPDATE `SHEET_TEMPLATE` SET name = ? WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setString(2, sheet.getName());
        });
        sheet.name = name;
    }

    private Map<Class, Map<String, String>> customSelections = null;

    public Map<Class, Map<String, String>> getSelectionAliases() {
        if (customSelections == null) {
            synchronized (this) {
                if (customSelections == null) {
                    customSelections = new ConcurrentHashMap<>();
                    db.query("SELECT * from SELECTION_ALIAS", stmt -> {}, (ThrowingConsumer<ResultSet>) rs -> {
                        String name = rs.getString("name");
                        String typeStr = rs.getString("type");
                        Class typeFinal = null;
                        for (Class<?> type : Locutus.cmd().getV2().getPlaceholders().getTypes()) {
                            if (type.getSimpleName().equalsIgnoreCase(typeStr)) {
                                typeFinal = type;
                                break;
                            }
                        }
                        if (typeFinal == null) {
                            return;
                        }
                        String selection = rs.getString("selection");
                        customSelections.computeIfAbsent(typeFinal, t -> new ConcurrentHashMap<>()).put(name, selection);
                    });
                }
            }
        }
        return customSelections;
    }

    public <T> SelectionAlias<T> getSelectionAlias(String name, Class<T> type) {
        // or null
        // CustomSelection(String name, Class<T> type, String selection)
        Map<String, String> selections = getSelectionAliases().get(type);
        if (selections == null) {
            return null;
        }
        String selection = selections.get(name);
        if (selection == null) {
            return null;
        }
        return new SelectionAlias<>(name, type, selection);
    }

    public void removeSelectionAlias(String name) {
        getSelectionAliases().values().forEach(map -> map.remove(name));
        db.update("DELETE FROM SELECTION_ALIAS WHERE name = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
        });
    }

    public void addSelectionAlias(String name, Class type, String selection) {
        db.update("INSERT INTO SELECTION_ALIAS(name, type, selection) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setString(2, type.getSimpleName());
            stmt.setString(3, selection);
        });
        getSelectionAliases().computeIfAbsent(type, t -> new ConcurrentHashMap<>()).put(name, selection);
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
        db.update("INSERT INTO CUSTOM_SHEET_TABS(sheet, tab, selector, template) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, sheet);
            stmt.setString(2, tab);
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
            sheets.put(rs.getString("name"), rs.getString("url"));
        });
        return sheets;
    }

    public String getCustomSheetUrl(String name) {
        AtomicReference<String> url = new AtomicReference<>();
        db.query("SELECT `url` FROM `CUSTOM_SHEET` WHERE `name` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            url.set(rs.getString("url"));
        });
        return url.get();
    }

    public CustomSheet getCustomSheet(String name) {
        String url = getCustomSheetUrl(name);
        if (url == null) {
            return null;
        }
        Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs = new HashMap<>();
        db.query("SELECT * FROM `CUSTOM_SHEET_TABS` WHERE `sheet` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            String tabName = rs.getString("tab");
            String selector = rs.getString("selector");
            String template = rs.getString("template");
            SelectionAlias selectionAlias = getSelectionAlias(selector, SelectionAlias.class);
            SheetTemplate sheetTemplate = getSheetTemplate(template);
            tabs.put(tabName, new AbstractMap.SimpleEntry<>(selectionAlias, sheetTemplate));
        });
        return new CustomSheet(name, url, tabs);
    }
}
