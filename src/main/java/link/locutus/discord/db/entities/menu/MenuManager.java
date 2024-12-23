package link.locutus.discord.db.entities.menu;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands.USER_MENU_STATE;

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
        boolean isUser = menu.title.equalsIgnoreCase("user");
        boolean isMessage = !isUser && menu.title.equalsIgnoreCase("message");
        if (isUser || isMessage) {
            Set<String> labels = new LinkedHashSet<>();
            for (Map.Entry<String, String> entry : menu.buttons.entrySet()) {
                if (labels.size() < 5) {
                    labels.add(entry.getKey());
                } else {
                    break;
                }
            }
            List<CommandData> commands = new ObjectArrayList<>();
            if (isUser) {
                for (String label : labels) {
                    commands.add(Commands.user(label));
                }
            } else {
                for (String label : labels) {
                    commands.add(Commands.message(label));
                }
            }
            RateLimitUtil.queue(db.getGuild().updateCommands().addCommands(commands));
        }
    }

    public AppMenu getAppMenu(String name) {
        String nameLower = name.toLowerCase();
        String query = "SELECT * FROM menus WHERE LOWER(title) = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            stmt.setString(1, nameLower);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return new AppMenu(rs);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return switch (name) {
            case "user" -> getUser();
            case "message" -> getMessage();
            default -> null;
        };
    }

    private AppMenu getUser() {
        return new AppMenu("user", "Select a command below", new Object2ObjectLinkedOpenHashMap<>(), 0, MenuState.NONE);
    }

    private AppMenu getMessage() {
        return new AppMenu("message", "Select a command below", new Object2ObjectLinkedOpenHashMap<>(), 0, MenuState.NONE);
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
