package link.locutus.discord.db.entities.menu;

import link.locutus.discord.db.GuildDB;

import java.util.Map;

public class MenuManager {
    private final GuildDB db;

    public MenuManager(GuildDB db) {
        this.db = db;
        createTables();
    }

    private void createTables() {
        // public String title;
        // public String description;
        // public Map<String, String> buttons; -> can be converted to a string WebUtil.GSON.toJson(buttons) and vice versa
        db.executeStmt("CREATE TABLE IF NOT EXISTS menus (menu_id BIGINT, title TEXT, description TEXT, buttons TEXT, last_used_channel BIGINT, last_message_id BIGINT, state TEXT, last_pressed_button TEXT)");
    }

    public void deleteMenu(AppMenu menu) {

    }

    public void saveMenu(AppMenu menu) {
        // Don't write code for this now
        // Save the menu to the database
        // upsert command labels to the discord guild
    }

    public AppMenu getAppMenu(String name) {
        // returns a menu devoid of user information, and in default state
    }

    public Map<String, AppMenu> getAppMenus() {
        // returns a menu devoid of user information, and in default state
    }
}
