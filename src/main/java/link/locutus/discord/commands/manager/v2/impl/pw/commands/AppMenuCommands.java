package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.menu.AppMenu;
import link.locutus.discord.db.entities.menu.MenuState;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AppMenuCommands {
    public static final PassiveExpiringMap<Long, AppMenu> USER_MENU_STATE = new PassiveExpiringMap<Long, AppMenu>(2, TimeUnit.MINUTES);

    public AppMenu getAppMenu(User user, IMessageIO io, boolean deleteIfDifferentChannel) {
        long channelId = 0;
        if (io instanceof DiscordHookIO hook) {
            channelId = hook.getIdLong();
        } else if (io instanceof DiscordChannelIO channelIo) {
            channelId = channelIo.getIdLong();
        } else {
            return null;
        }
        AppMenu menu = USER_MENU_STATE.get(user.getIdLong());
        if (menu != null && deleteIfDifferentChannel && menu.getChannelId() != (channelId)) {
            USER_MENU_STATE.remove(user.getIdLong());
            return null;
        }
        return menu;
    }

    public boolean onCommand(IMessageIO io, GuildDB db, User user, MessageChannel channel, ICommand command, Map<String, String> args) {
        AppMenu menu = getAppMenu(user, io, false);
        if (menu == null || menu.getChannelId() != channel.getIdLong()) {
            return false;
        }

        if (menu.state != MenuState.ADD_BUTTON) {
            return false;
        }

        String buttonLabel = args.get("label");
        String buttonCommand = args.get("command");
        if (buttonLabel != null && buttonCommand != null) {
            menu.buttons.put(buttonLabel, buttonCommand);
            db.getMenuManager().saveMenu(menu);
            sendMenuInfo(io, menu, null, true, false);
            return true;
        }

        return false;
    }

    // Placeholder for now, don't code these now
    public static ICommand OPEN_MENU = null;
    public static ICommand EDIT_MENU = null;
    public static ICommand ADD_BUTTON = null;
    public static ICommand REMOVE_BUTTON = null;
    public static ICommand RENAME_BUTTON = null;
    public static ICommand SWAP_BUTTONS = null;
    public static ICommand NEW_MENU = null;
    public static ICommand DELETE_MENU = null;
    public static ICommand RENAME_MENU = null;
    public static ICommand DESCRIBE_MENU = null;
    public static ICommand SET_MENU_STATE = null;

    public boolean onButtonUse(IMessageIO io, GuildDB db, User user, String buttonLabel) {
        AppMenu menu = getAppMenu(user, io, false);
        if (menu == null) {
            return false;
        }

        MenuState state = menu.state;
        switch (state) {
            case REORDER:
                if (menu.lastPressedButton != null) {
                    swapMenuButtons(io, db, user, menu, menu.lastPressedButton, buttonLabel);
                    break;
                }
                menu.lastPressedButton = buttonLabel;
                String msg = """
                        You are now in reordering mode. Click the buttons to swap their positions. Click the same button again to exit reordering mode.
                        Press cancel to exit. This dialog will timeout after 2 minutes.
                        """;
                sendMenuInfo(io, menu, msg, true, true);
                // Reorder the buttons
                // Implement reordering logic here
                break;
            case ADD_BUTTON:
                // Not a valid interaction, return false
                return false;
            case REMOVE_BUTTON:
                // Remove the button
                menu.buttons.remove(buttonLabel);
                // save the menu
                db.getMenuManager().saveMenu(menu);
                // Send the updated menu
                sendMenuInfo(io, menu, null, true, true);
                break;
            case RENAME_BUTTON:
                // Rename the button
                menu.lastPressedButton = buttonLabel; // Store the button to be renamed
                // Open a modal to get the new name
                CommandRef renameModal = RENAME_BUTTON.menu(menu.title).label(buttonLabel).new_label("");
                io.create().modal(CommandBehavior.EPHEMERAL, renameModal, "Rename Modal").send();

                break;
            default:
                return false;
        }
        return true;
    }

    public Set<String> getMenuLabels(User user) {
        // either Set<String> or null if no menu is open
        AppMenu menu = USER_MENU_STATE.get(user.getIdLong());
        if (menu == null) {
            return null;
        }
        return menu.buttons.keySet();
    }

    public void sendMenuInfo(IMessageIO io, AppMenu menu, String overrideDescription, boolean showEdit, boolean showCancel) {
        // Send the menu info to the user
        // Use io.embed() to send the menu info

        // showCancel = set state to NONE
        String desc = overrideDescription != null ? overrideDescription : menu.description;
        IMessageBuilder msg = io.create().embed(menu.title, desc);
        for (Map.Entry<String, String> entry : menu.buttons.entrySet()) {
            String label = entry.getKey();
            String command = entry.getValue();
            msg.commandButton(CommandBehavior.EPHEMERAL, label, command);
        }
    }

    @Command
    @NoFormat
    @Ephemeral
    public void openMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu) {
        menu.lastUsedChannel = io.getIdLong();
        menu.state = MenuState.NONE;
        USER_MENU_STATE.put(user.getIdLong(), menu);
        sendMenuInfo(io, menu, null, menu.canEdit(db, user), false);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void deleteMenu(@Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, @Me User user, AppMenu menu, @Switch("f") boolean force) {
        if (!force) {
            String title = "Delete Menu";
            String body = "Are you sure you want to delete the menu `" + menu.title + "`?";
            io.create().confirmation(title, body, command).send();
            return;
        }
        db.getMenuManager().deleteMenu(menu);
        return;
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void renameMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, String name) {
        // rename the menu
        // save it to db
        // return menu embed
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void describeMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, String description) {
        // set menu description
        // save it to db
        // return menu embed
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void setMenuState(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, MenuState state) {
        // set the menu state, clear prior state
        // return menu embed
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void editMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu) {
        String title = "Edit Menu";
        String description = "Edit the menu details";

        // A command will run a command, a modal will open a prompt for an input and then run a command
        CommandRef renameModel = RENAME_MENU.menu(menu.title).name("");
        CommandRef describeModel = DESCRIBE_MENU.menu(menu.title).description("");
        CommandRef reorderCommand = SET_MENU_STATE.menu(menu.title).state(MenuState.REORDER.name());
        CommandRef addButtonCommand = SET_MENU_STATE.menu(menu.title).state(MenuState.ADD_BUTTON.name());
        CommandRef removeButtonCommand = SET_MENU_STATE.menu(menu.title).state(MenuState.REMOVE_BUTTON.name());
        CommandRef renameButtonCommand = SET_MENU_STATE.menu(menu.title).state(MenuState.RENAME_BUTTON.name());
        CommandRef deleteMenu = DELETE_MENU.menu(menu.title); // Don't force, let delete prompt for confirmation

        io.create().embed(title, description)
                .modal(CommandBehavior.EPHEMERAL, renameModel, "Rename Menu")
                .modal(CommandBehavior.EPHEMERAL, describeModel, "Menu Description")
                .commandButton(CommandBehavior.EPHEMERAL, reorderCommand, "Reorder Buttons")
                .commandButton(CommandBehavior.EPHEMERAL, addButtonCommand, "Add Button")
                .commandButton(CommandBehavior.EPHEMERAL, removeButtonCommand, "Remove Button")
                .commandButton(CommandBehavior.EPHEMERAL, renameButtonCommand, "Rename Button")
                .commandButton(CommandBehavior.EPHEMERAL, deleteMenu, "Delete Menu")
                .send();
    }


    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void addMenuButton(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label, String command) {
        if (menu.state != MenuState.ADD_BUTTON) {
            io.create().embed("Error", "Menu is not in a state to add buttons.").send();
            return;
        }

        menu.buttons.put(label, command);
        db.getMenuManager().saveMenu(menu);
        sendMenuInfo(io, menu, "Button added successfully.", true, true);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void removeMenuButton(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label) {
        if (menu.state != MenuState.REMOVE_BUTTON) {
            io.create().embed("Error", "Menu is not in a state to remove buttons.").send();
            return;
        }

        if (menu.buttons.remove(label) != null) {
            db.getMenuManager().saveMenu(menu);
            sendMenuInfo(io, menu, "Button removed successfully.", true, true);
        } else {
            io.create().embed("Error", "Button not found.").send();
        }
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void swapMenuButtons(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label1, @MenuLabel String label2) {
        if (menu.state != MenuState.REORDER) {
            io.create().embed("Error", "Menu is not in a state to reorder buttons.").send();
            return;
        }

        String command1 = menu.buttons.get(label1);
        String command2 = menu.buttons.get(label2);

        if (command1 == null || command2 == null) {
            io.create().embed("Error", "One or both buttons not found.").send();
            return;
        }

        menu.buttons.put(label1, command2);
        menu.buttons.put(label2, command1);

        db.getMenuManager().saveMenu(menu);
        sendMenuInfo(io, menu, "Buttons swapped successfully.", true, true);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void renameMenuButton(@Me IMessageIO io, @Me GuildDB db, @Me User user, @MenuLabel String label, String new_label) {
        AppMenu menu = getAppMenu(user, null, false);
        if (menu == null || menu.state != MenuState.RENAME_BUTTON) {
            io.create().embed("Error", "Menu is not in a state to rename buttons.").send();
            return;
        }

        String command = menu.buttons.remove(label);
        if (command == null) {
            io.create().embed("Error", "Button not found.").send();
            return;
        }

        menu.buttons.put(new_label, command);
        db.getMenuManager().saveMenu(menu);
        sendMenuInfo(io, menu, "Button renamed successfully.", true, true);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void newMenu(@Me IMessageIO io, @Me GuildDB db, String name, String description) {
        AppMenu existing = db.getMenuManager().getAppMenu(name);
        if (existing != null) {
            throw new IllegalArgumentException("Menu already exists with name: `" + name + "`. Pick a different name or edit that one instead.");
        }
        AppMenu newMenu = new AppMenu(name, description, new ConcurrentHashMap<>(), 0, MenuState.NONE);
        db.getMenuManager().saveMenu(newMenu);
        io.create().embed("Success", "New menu created successfully.").send();
    }

    @Command
    @NoFormat
    @Ephemeral
    public String cancel(@Me IMessageIO io, @Me GuildDB db, User user) {
        AppMenu menu = AppMenuCommands.USER_MENU_STATE.get(user.getIdLong());
        if (menu != null) {
            menu.clearState();
            sendMenuInfo(io, menu, null, menu.canEdit(db, user), false);
            return null;
        } else {
            return "No menu to cancel. (Is it old?)";
        }
    }
}
