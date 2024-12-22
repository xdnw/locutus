package link.locutus.discord.db.entities.menu;

import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.LabelArgs;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.User;

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

    public boolean canEdit(GuildDB db, User user) {
        return Roles.ADMIN.has(user, db.getGuild());
    }

    public class Send {
        public String overrideDescription;
        public boolean showButtons;
        public boolean showEditButton;

        public boolean showEditOptions;
        // delete
        // swap
        // title
        // desc
        // etc.

        public boolean showCancelButton;

        public IMessageBuilder send(IMessageIO io) {
            String desc = overrideDescription != null ? overrideDescription : description;
            IMessageBuilder msg = io.create().embed(title, desc);
            if (showButtons) {
                for (Map.Entry<String, String> entry : buttons.entrySet()) {
                    String label = entry.getKey();
                    String command = entry.getValue();
                    msg.commandButton(CommandBehavior.EPHEMERAL, label, command);
                }
            }
            if (showEditButton) {
                msg.commandButton(CommandBehavior.EPHEMERAL, "Edit", AppMenuCommands.EDIT_MENU.menu(title));
            }
            if (showEditOptions) {

            }
            if (showCancelButton) {
                msg.commandButton(CommandBehavior.EPHEMERAL, "Cancel", AppMenuCommands.CANCEL.menu(title));
            }
        }
    }
}
