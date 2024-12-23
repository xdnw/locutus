package link.locutus.discord.db.entities.menu;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.web.WebUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public class MenuManager {
    private final GuildDB db;

    public MenuManager(GuildDB db) {
        this.db = db;
        createTables();
    }

    private void createTables() {
        db.executeStmt("CREATE TABLE IF NOT EXISTS menus (title TEXT NOT NULL PRIMARY KEY, description TEXT NOT NULL, buttons TEXT NOT NULL)");
    }

    public void deleteMenu(AppMenu menu) {
        db.executeStmt("DELETE FROM menus WHERE title = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement f) throws Exception {
                f.setString(1, menu.title);
            }
        });
    }

    public void saveMenu(AppMenu menu) {
        String buttons = WebUtil.GSON.toJson(menu.buttons);
        db.executeStmt("INSERT OR REPLACE INTO menus (title, description, buttons) VALUES (?, ?, ?)", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement f) throws Exception {
                f.setString(1, menu.title);
                f.setString(2, menu.description);
                f.setString(3, buttons);
            }
        });
    }

    public AppMenu getAppMenu(String name) {
        String query = "SELECT * FROM menus WHERE title = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return new AppMenu(rs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, AppMenu> getAppMenus() {
        Map<String, AppMenu> menus = new Object2ObjectLinkedOpenHashMap<>();
        db.query("SELECT * FROM menus", null, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws Exception {
                while (rs.next()) {
                    AppMenu menu = new AppMenu(rs);
                    menu.clearState();
                    menus.put(menu.title, menu);
                }
            }
        });
        return menus;
    }

    public void renameMenu(AppMenu menu, String name) {
        db.executeStmt("UPDATE menus SET title = ? WHERE title = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement f) throws Exception {
                f.setString(1, name);
                f.setString(2, menu.title);
            }
        });
        menu.title = name;
    }
}
