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

import java.util.*;
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
            menu.setState(MenuState.NONE).message().buttons(true).edit(menu.canEdit(db, user)).queue(io);
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
                menu.lastPressedButton = buttonLabel.toLowerCase(Locale.ROOT);
                String msg = """
                        You are now in reordering mode. Click the buttons to swap their positions. Click the same button again to exit reordering mode.
                        Press `cancel` to exit.
                        """;
                menu.setState(MenuState.REORDER).message().buttons(true).cancel(true).description(msg).queue(io);
                break;
            case REMOVE_BUTTON:
                removeMenuButton(io, db, user, menu, buttonLabel);
                break;
            case RENAME_BUTTON:
                // Rename the button
                menu.lastPressedButton = buttonLabel; // Store the button to be renamed
                // Open a modal to get the new name
//                CommandRef renameModal = RENAME_BUTTON.menu(menu.title).label(buttonLabel).new_label("");  // TODO FIXME APP MENU COMMAND
//                io.create().modal(CommandBehavior.EPHEMERAL, renameModal, "Rename Modal").send();
                break;
            default:
                return false;
        }
        return true;
    }

    @Command
    @NoFormat
    @Ephemeral
    public void openMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu) {
        menu.lastUsedChannel = io.getIdLong();
        menu.state = MenuState.NONE;
        USER_MENU_STATE.put(user.getIdLong(), menu);
        menu.message().edit(menu.canEdit(db, user)).buttons(true).queue(io);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public String deleteMenu(@Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, @Me User user, AppMenu menu, @Switch("f") boolean force) {
        if (!force) {
            String title = "Delete Menu";
            String body = "Are you sure you want to delete the menu `" + menu.title + "`?";
            io.create().confirmation(title, body, command).send();
            return null;
        }
        db.getMenuManager().deleteMenu(menu);
        return "Menu deleted successfully.";
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void renameMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, String name) {
        menu.setState(MenuState.NONE);
        db.getMenuManager().renameMenu(menu, name);
        menu.message().edit(menu.canEdit(db, user)).buttons(true).queue(io);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void describeMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, String description) {
        menu.setState(MenuState.NONE);
        menu.description = description.replace("\\n", "\n");
        db.getMenuManager().saveMenu(menu);
        menu.message().edit(menu.canEdit(db, user)).buttons(true).queue(io);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void setMenuState(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, MenuState state) {
        menu.messageState(io, db, user, state);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void editMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu) {
        menu.setState(MenuState.NONE);
        menu.message().options(true).cancel(true).queue(io);
    }


    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void addMenuButton(@Me IMessageIO io, @Me JSONObject cmdJson, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label, @Default String command, @Switch("f") boolean force) {
        label = label.toLowerCase(Locale.ROOT);
        menu.setState(MenuState.NONE);
        if (label.equalsIgnoreCase("cancel") || label.equalsIgnoreCase("edit")) {
            menu.setState(MenuState.NONE);
            menu.message().options(true).cancel(true).description("You cannot use `cancel` or `edit` as button labels. Please try again").queue(io);
            return;
        }
        String contains = null;
        for (String button : menu.buttons.keySet()) {
            if (button.equalsIgnoreCase(label)) {
                contains = button;
                break;
            }
        }
        if (contains != null) {
            if (!force) {
                io.create().confirmation("Button already exists", "A button with the label `" + label + "` already exists. Do you want to overwrite it?", cmdJson).send();
                return;
            }
            menu.buttons.remove(contains);
            db.getMenuManager().saveMenu(menu);
        }
        if (command == null || command.isEmpty()) {
            String desc = "Enter the SLASH command for the button `" + label + "` as you would normally, within the same channel\nNote: Legacy commands are not supported";
            menu.message().buttons(true).edit(true).description(desc).queue(io);
        } else {
            menu.buttons.put(label, command);
            db.getMenuManager().saveMenu(menu);
            String desc = "Button `" + label + "` added successfully.\n\nPress a button to remove it.\nPress `cancel` to exit";
            menu.message().options(true).cancel(true).description(desc).queue(io);
        }
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void removeMenuButton(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label) {
        label = label.toLowerCase(Locale.ROOT);
        menu.setState(MenuState.REMOVE_BUTTON);
        if (menu.buttons.remove(label) != null) {
            db.getMenuManager().saveMenu(menu);
            String desc = "Removed button `" + label + "`.\n\nPress a button to remove it.\nPress `cancel` to exit";
            menu.message().buttons(true).cancel(true).description(desc).queue(io);
        } else {
            menu.message().buttons(true).cancel(true).description("Error: Button not found. Try again.\nPress a button to remove it.\nPress  `cancel` to exit").queue(io);
        }
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void swapMenuButtons(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label1, @MenuLabel String label2) {
        label1 = label1.toLowerCase(Locale.ROOT);
        label2 = label2.toLowerCase(Locale.ROOT);
        if (label1.equals(label2)) {
            menu.clearState().message().options(true).description("Cancelled reordering (pressed button twice)\n\n" +
                    "Select an edit option.\nPress `cancel` to exit").cancel(true).queue(io);
            return;
        }
        String command1 = menu.buttons.get(label1);
        String command2 = menu.buttons.get(label2);

        if (command1 == null || command2 == null) {
            List<String> cannotFind = new ArrayList<>();
            if (command1 == null) {
                cannotFind.add(label1);
            }
            if (command2 == null) {
                cannotFind.add(label2);
            }
            String errorMsg = "Cannot find button(s): " + String.join(", ", cannotFind);
            String desc = errorMsg + "\n\nSelect a button to swap.\nPress `cancel` to exit.";
            menu.setState(MenuState.REORDER).message().options(true).description(desc).cancel(true).queue(io);
            return;
        }

        menu.buttons.put(label1, command2);
        menu.buttons.put(label2, command1);

        db.getMenuManager().saveMenu(menu);
        menu.setState(MenuState.REORDER).message()
                .options(true)
                .description("Successfully swapped `" + label1 + "` and `" + label2 + "`.\n\nSelect a button to swap.\nPress `cancel` to exit.")
                .cancel(true).queue(io);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void renameMenuButton(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label, String new_label) {
        label = label.toLowerCase(Locale.ROOT);
        if (!menu.buttons.containsKey(label)) {
            String desc = "Cannot find button: `" + label + "`.\n\nSelect a button to rename.\nPress `cancel` to exit.";
            menu.setState(MenuState.RENAME_BUTTON).message().buttons(true).cancel(true).description(desc).queue(io);
            return;
        }
        if (menu.buttons.containsKey(new_label)) {
            String desc = "Button with label `" + new_label + "` already exists. Pick another name.\n\nSelect a button to rename.\nPress `cancel` to exit.";
            menu.setState(MenuState.RENAME_BUTTON).message().buttons(true).cancel(true).description(desc).queue(io);
            return;
        }
        if (new_label.equalsIgnoreCase("cancel") || new_label.equalsIgnoreCase("edit")) {
            String desc = "You cannot use `cancel` or `edit` as button labels. Please try again.\n\nSelect a button to rename.\nPress `cancel` to exit.";
            menu.setState(MenuState.RENAME_BUTTON).message().buttons(true).cancel(true).description(desc).queue(io);
            return;
        }
        String cmd1 = menu.buttons.remove(label);
        menu.buttons.put(new_label, cmd1);
        db.getMenuManager().saveMenu(menu);
        String desc = "Successfully rename `" + label + "` to `" + new_label + "`.\n\nSelect a button to rename.\nPress `cancel` to exit.";
        menu.setState(MenuState.RENAME_BUTTON).message().options(true).description(desc).cancel(true).queue(io);
    }

    @Command
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void newMenu(@Me IMessageIO io, @Me User user, @Me GuildDB db, String name, String description) {
        AppMenu existing = db.getMenuManager().getAppMenu(name);
        if (existing != null) {
            throw new IllegalArgumentException("Menu already exists with name: `" + name + "`. Pick a different name or edit that one instead.");
        }
        AppMenu newMenu = new AppMenu(name, description, new ConcurrentHashMap<>(), 0, MenuState.NONE);
        USER_MENU_STATE.put(user.getIdLong(), newMenu);
        db.getMenuManager().saveMenu(newMenu);
        newMenu.message().edit(true).cancel(true).queue(io);
    }

    @Command
    @NoFormat
    @Ephemeral
    public void cancel(@Me IMessageIO io, @Me GuildDB db, User user, AppMenu menu) {
        if (menu.state == MenuState.NONE) {
            menu.clearState().message().buttons(true).edit(menu.canEdit(db, user)).queue(io);
        } else {
            menu.clearState().message().options(true).cancel(true).queue(io);
        }
    }
}
