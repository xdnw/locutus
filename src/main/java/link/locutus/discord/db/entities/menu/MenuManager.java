package link.locutus.discord.db.entities.menu;

import link.locutus.discord.db.GuildDB;

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
}
