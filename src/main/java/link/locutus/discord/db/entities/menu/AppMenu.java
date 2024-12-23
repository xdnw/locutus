package link.locutus.discord.db.entities.menu;

import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.AppMenuCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.user.Roles;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    public AppMenu(ResultSet rs) throws SQLException {
        this.title = rs.getString("title");
        this.description = rs.getString("description");
        String buttons = rs.getString("buttons");
        this.buttons = WebUtil.GSON.fromJson(buttons, Map.class);
        state = MenuState.NONE;
    }

    public AppMenu setState(MenuState state) {
        this.state = state;
        this.lastPressedButton = null;
        return this;
    }

    public AppMenu clearState() {
        lastUsedChannel = 0;
        lastMessageId = 0;
        state = MenuState.NONE;
        lastPressedButton = null;
        return this;
    }

    public long getChannelId() {
        return lastUsedChannel;
    }

    public boolean canEdit(GuildDB db, User user) {
        return Roles.ADMIN.has(user, db.getGuild());
    }

    public Send message() {
        return new Send();
    }

    public CompletableFuture<IMessageBuilder> messageState(IMessageIO io, GuildDB db, User user, MenuState state) {
        this.state = state;
        Send msg = message();
        boolean canEdit = canEdit(db, user);
        switch (state) {
            case NONE -> {
                msg.buttons(true).edit(canEdit);
            }
            case REORDER -> {
                String desc;
                if (lastPressedButton != null) {
                    desc = "Select a button to swap with `" + lastPressedButton + "`";
                } else {
                    desc = "Select a button to swap.";
                }
                msg.description(desc).buttons(true).cancel(true);
            }
            case ADD_BUTTON -> {
                msg.description("Enter the button label.").options(true).cancel(true);
            }
            case REMOVE_BUTTON -> {
                msg.description("Select a button to remove.").buttons(true).cancel(true);
            }
            case RENAME_BUTTON -> {
                msg.description("Select a button to rename.").buttons(true).cancel(true);
            }
        }
        return msg.queue(io);
    }

    public class Send {
        public String overrideDescription;
        public boolean showButtons;
        public boolean showEditButton;

        public boolean showEditOptions;

        public boolean showCancelButton;

        public Send description(String desc) {
            this.overrideDescription = desc;
            return this;
        }

        public Send buttons(boolean show) {
            this.showButtons = show;
            return this;
        }

        public Send edit(boolean show) {
            this.showEditButton = show;
            return this;
        }

        public Send options(boolean show) {
            this.showEditOptions = show;
            return this;
        }

        public Send cancel(boolean show) {
            this.showCancelButton = show;
            return this;
        }

        public CompletableFuture<IMessageBuilder> queue(IMessageIO io) {
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
//                msg.commandButton(CommandBehavior.EPHEMERAL, "Edit", AppMenuCommands.EDIT_MENU.menu(title)); // TODO FIXME APP MENU COMMAND
            }
            if (showEditOptions) {
//                CommandRef renameModel = AppMenuCommands.RENAME_MENU.menu(title).name("");
//                CommandRef describeModel = AppMenuCommands.DESCRIBE_MENU.menu(title).description("");
//                CommandRef reorderCommand = AppMenuCommands.SET_MENU_STATE.menu(title).state(MenuState.REORDER.name());
//                CommandRef addButtonCommand = AppMenuCommands.SET_MENU_STATE.menu(title).state(MenuState.ADD_BUTTON.name());
//                CommandRef removeButtonCommand = AppMenuCommands.SET_MENU_STATE.menu(title).state(MenuState.REMOVE_BUTTON.name());
//                CommandRef renameButtonCommand = AppMenuCommands.SET_MENU_STATE.menu(title).state(MenuState.RENAME_BUTTON.name());
//                CommandRef deleteMenu = AppMenuCommands.DELETE_MENU.menu(title); // Don't force, let delete prompt for confirmation
//
//                msg.modal(CommandBehavior.EPHEMERAL, renameModel, "Rename Menu")
//                   .modal(CommandBehavior.EPHEMERAL, describeModel, "Menu Description")
//                   .commandButton(CommandBehavior.EPHEMERAL, reorderCommand, "Reorder Buttons")
//                   .commandButton(CommandBehavior.EPHEMERAL, addButtonCommand, "Add Button")
//                   .commandButton(CommandBehavior.EPHEMERAL, removeButtonCommand, "Remove Button")
//                   .commandButton(CommandBehavior.EPHEMERAL, renameButtonCommand, "Rename Button")
//                   .commandButton(CommandBehavior.EPHEMERAL, deleteMenu, "Delete Menu")
//                   .send();
            }
            if (showCancelButton) {
//                msg.commandButton(CommandBehavior.EPHEMERAL, "Cancel", AppMenuCommands.CANCEL.menu(title));  // TODO FIXME APP MENU COMMAND
            }
            return msg.send();
        }
    }
}
