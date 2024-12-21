package link.locutus.discord.db.entities.menu;

import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.db.entities.LabelArgs;

import java.util.Map;

public class AppMenu {
    public String title;
    public String description;
    public Map<String, String> buttons;

    public long lastUsedChannel;
    public long lastMessageId;

    public MenuState state;
    public String lastPressedButton;

    public AppMenu(String title, String description, Map<String, String> buttons, long lastUsedChannel, MenuState state) {
        this.title = title;
        this.description = description;
        this.buttons = buttons;
        this.lastUsedChannel = lastUsedChannel;
        this.state = state;
    }

    public void setState(MenuState state) {
        this.state = state;
        // clear lastPressedButton
        this.lastPressedButton = null;
    }

    public void clearState() {
        lastUsedChannel = 0;
        lastMessageId = 0;
        state = MenuState.NONE;
        lastPressedButton = null;
    }

    public long getChannelId() {
        return lastUsedChannel;
    }
}
