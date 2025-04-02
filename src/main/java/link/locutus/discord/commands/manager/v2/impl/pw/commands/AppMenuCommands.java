package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
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
    public static final PassiveExpiringMap<Long, AppMenu> USER_MENU_STATE = new PassiveExpiringMap<Long, AppMenu>(15, TimeUnit.MINUTES);

//    public boolean onButtonUse(IMessageIO io, GuildDB db, User user, String buttonLabel) {
//        AppMenu menu = getAppMenu(user, io, false);
//        if (menu == null) {
//            return false;
//        }
//        MenuState state = menu.state;
//        switch (state) {
//            case REORDER:
//                if (menu.lastPressedButton != null) {
//                    swapMenuButtons(io, db, user, menu, menu.lastPressedButton, buttonLabel);
//                    break;
//                }
//                menu.lastPressedButton = buttonLabel.toLowerCase(Locale.ROOT);
//                String msg = """
//                        You are now in reordering mode. Click the buttons to swap their positions. Click the same button again to exit reordering mode.
//                        Press `cancel` to exit.
//                        """;
//                menu.setState(MenuState.REORDER).message().buttons(true).cancel(true).description(msg).queue(db.getGuild(), user, io);
//                break;
//            case REMOVE_BUTTON:
//                removeMenuButton(io, db, user, menu, buttonLabel);
//                break;
//            case RENAME_BUTTON:
//                // Rename the button
//                menu.lastPressedButton = buttonLabel; // Store the button to be renamed
//                // Open a modal to get the new name
//                CommandRef renameModal = CM.menu.button.rename.cmd.menu(menu.title).label(buttonLabel).new_label("");
//                io.create().modal(CommandBehavior.EPHEMERAL, renameModal, "Rename Modal").send();
//                break;
//            default:
//                return false;
//        }
//        return true;
//    }

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
            menu.setState(MenuState.NONE).message().buttons(true).edit(menu.canEdit(db, user)).queue(db.getGuild(), user, io);
            return true;
        }

        return false;
    }

    @Command(desc = "Open a custom saved menu for the discord guild", viewable = true)
    @NoFormat
    @Ephemeral
    public void openMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu) {
        menu.lastUsedChannel = io.getIdLong();
        menu.setState(MenuState.NONE);
        USER_MENU_STATE.put(user.getIdLong(), menu);
        menu.message().edit(menu.canEdit(db, user)).buttons(true).queue(db.getGuild(), user, io);
    }

    @Command(desc = "Delete a custom discord menu set in the guild, with confirmation")
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
        if (menu.isDefault()) {
            return "Cannot delete the default menus. Consider editing them instead.";
        }
        db.getMenuManager().deleteMenu(menu);
        return "Menu deleted successfully.";
    }

    @Command(desc = "Rename a custom discord menu set in the guild")
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void renameMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, String name) {
        if (menu.isDefault()) {
            throw new IllegalArgumentException("Cannot rename the default menus. Consider creating a new one with " + CM.menu.create.cmd.toSlashMention());
        }
        menu.setState(MenuState.NONE);
        db.getMenuManager().renameMenu(menu, name);
        menu.message().edit(menu.canEdit(db, user)).buttons(true).queue(db.getGuild(), user, io);
    }

    @Command(desc = "Set the description for a custom discord menu set in the guild")
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void describeMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, String description) {
        menu.setState(MenuState.NONE);
        menu.description = description.replace("\\n", "\n");
        db.getMenuManager().saveMenu(menu);
        menu.message().edit(menu.canEdit(db, user)).buttons(true).queue(db.getGuild(), user, io);
    }

    @Command(desc = "Set and display the temporary menu context for yourself\n" +
            "This determines the buttons and their actions, for editing the menu, such as removing, renaming or swapping buttons", viewable = true)
    @NoFormat
    @Ephemeral
    public void setMenuState(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, MenuState state) {
        if (!Roles.ADMIN.has(user, db.getGuild()) && state != MenuState.NONE) {
            throw new IllegalArgumentException("You do not have permission to set the menu state");
        }
        menu.messageState(io, db, user, state);
    }

    @Command(desc = "Display the custom discord menu edit options for the guild")
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void editMenu(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu) {
        menu.setState(MenuState.NONE);
        menu.message().options(true).cancel(true).queue(db.getGuild(), user, io);
    }


    @Command(desc = "Add a labelled button to the custom discord menu set in the guild\n" +
            "Leave the command empty to enter the command in the next step")
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public static void addMenuButton(@Me IMessageIO io, @Me JSONObject cmdJson, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label, @Default String command, @Switch("f") boolean force) {
        label = label.toLowerCase(Locale.ROOT);
        if (label.equalsIgnoreCase("cancel") || label.equalsIgnoreCase("edit")) {
            menu.setState(MenuState.NONE);
            menu.message().options(true).cancel(true).description("You cannot use `cancel` or `edit` as button labels. Please try again").queue(db.getGuild(), user, io);
            return;
        }
        if (!label.matches("^[-_ \\p{L}\\p{N}\\p{sc=Deva}\\p{sc=Thai}]{1,32}$")) {
            menu.setState(MenuState.NONE);
            menu.message().buttons(true).cancel(true).description("Button labels can only contain alphanumeric characters and spaces, not `" + label + "`.\n\nSelect a button to add.\nPress `cancel` to exit").queue(db.getGuild(), user, io);
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
        } else if (menu.buttons.size() >= 25) {
            menu.setState(MenuState.NONE);
            menu.message().options(true).cancel(true).description("You cannot add more than 25 buttons to a menu. Consider creating a new menu as a sub-menu for some of the buttons").queue(db.getGuild(), user, io);
        }
        if (command == null || command.isEmpty()) {
            menu.setState(MenuState.ADD_BUTTON);
            menu.lastPressedButton = label;
            String desc = "Enter the SLASH command for the button `" + label + "` as you would normally, within the same channel\n" +
                    "You can use: `{user}`, `{message}` and `{content}` if the menu is from a user or message interaction\n" +
                    "Or any nation placeholder if the `{user}` is registered\n\n" +
                    "Note: Legacy commands are not supported\n\n" +
                    "Alternatively, cancel, and run " + CM.menu.button.add.cmd.toSlashMention() + " and specify the command";
            menu.message().buttons(true).cancel(true).description(desc).queue(db.getGuild(), user, io);
        } else {
            String footer = "";
            if (menu.isDefault()) {
                footer = "\n\nYou may need to restart discord to see changes to context menus";
            }

            menu.setState(MenuState.NONE);
            menu.buttons.put(label, command);
            db.getMenuManager().saveMenu(menu);
            String desc = "Button `" + label + "` added successfully.\n\n" +
                    "Press a button to remove it.\nPress `cancel` to exit" + footer;
            menu.message().options(true).cancel(true).description(desc).queue(db.getGuild(), user, io);
        }
    }

    @Command(desc = "Remove a button from the custom discord menu set in the guild")
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void removeMenuButton(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label) {
        label = label.toLowerCase(Locale.ROOT);
        menu.setState(MenuState.REMOVE_BUTTON);
        if (menu.buttons.remove(label) != null) {
            String footer = "";
            if (menu.isDefault()) {
                footer = "\n\nYou may need to restart discord to see changes to context menus";
            }

            db.getMenuManager().saveMenu(menu);
            String desc = "Removed button `" + label + "`.\n\nPress a button to remove it.\nPress `cancel` to exit" + footer;
            menu.message().buttons(true).cancel(true).description(desc).queue(db.getGuild(), user, io);
        } else {
            menu.message().buttons(true).cancel(true).description("Error: Button not found. Try again.\nPress a button to remove it.\nPress  `cancel` to exit").queue(db.getGuild(), user, io);
        }
    }

    @Command(desc = "Swap the positions of two buttons in the custom discord menu set in the guild\n" +
            "Leave the second button empty to prompt for the second button (via pressing it)")
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void swapMenuButtons(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label1, @Default @MenuLabel String label2) {
        label1 = label1.toLowerCase(Locale.ROOT);
        if (label2 != null) label2 = label2.toLowerCase(Locale.ROOT);
        String initialLabel2 = label2;
        if (label2 == null) label2 = menu.lastPressedButton;

        if (label2 != null && label1.equals(label2)) {
            menu.clearState().message().options(true).description("Cancelled reordering (pressed button twice)\n\n" +
                    "Select an edit option.\nPress `cancel` to exit").cancel(true).queue(db.getGuild(), user, io);
            return;
        }
        String command1 = menu.buttons.get(label1);
        String command2 = label2 == null ? null : menu.buttons.get(label2);

        if (command1 == null || (command2 == null && initialLabel2 == null)) {
            List<String> cannotFind = new ArrayList<>();
            if (command1 == null) {
                cannotFind.add(label1);
            }
            if (command2 == null) {
                if (initialLabel2 == null) menu.lastPressedButton = null;
                cannotFind.add(label2);
            }
            String errorMsg = "Cannot find button(s): " + String.join(", ", cannotFind);
            String desc = errorMsg + "\n\nSelect a button to swap.\nPress `cancel` to exit.";
            menu.state = MenuState.REORDER;
            menu.message().buttons(true).description(desc).cancel(true).queue(db.getGuild(), user, io);
            return;
        }
        if (label2 == null) {
            menu.state = MenuState.REORDER;
            menu.lastPressedButton = label1;
            String desc = "Selected `" + label1 + "`. Select a button to swap with.\nPress `cancel` to exit.";
            menu.message().buttons(true).description(desc).cancel(true).queue(db.getGuild(), user, io);
            return;
        }
        String footer = "";
        if (menu.isDefault()) {
            footer = "\n\nYou may need to restart discord to see changes to context menus";
        }

        menu.buttons.put(label1, command2);
        menu.buttons.put(label2, command1);

        db.getMenuManager().saveMenu(menu);
        menu.setState(MenuState.REORDER);
        menu.message()
                .options(true)
                .description("Successfully swapped `" + label1 + "` and `" + label2 +
                        "`.\n\nSelect a button to swap.\nPress `cancel` to exit." + footer)
                .cancel(true).queue(db.getGuild(), user, io);
    }

    @Command(desc = "Rename a button in the custom discord menu set in the guild")
    @NoFormat
    @RolePermission(Roles.ADMIN)
    @Ephemeral
    public void renameMenuButton(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu, @MenuLabel String label, String new_label) {
        label = label.toLowerCase(Locale.ROOT);
        if (!menu.buttons.containsKey(label)) {
            String desc = "Cannot find button: `" + label + "`.\n\nSelect a button to rename.\nPress `cancel` to exit.";
            menu.setState(MenuState.RENAME_BUTTON).message().buttons(true).cancel(true).description(desc).queue(db.getGuild(), user, io);
            return;
        }
        if (menu.buttons.containsKey(new_label)) {
            String desc = "Button with label `" + new_label + "` already exists. Pick another name.\n\nSelect a button to rename.\nPress `cancel` to exit.";
            menu.setState(MenuState.RENAME_BUTTON).message().buttons(true).cancel(true).description(desc).queue(db.getGuild(), user, io);
            return;
        }
        if (new_label.equalsIgnoreCase("cancel") || new_label.equalsIgnoreCase("edit")) {
            String desc = "You cannot use `cancel` or `edit` as button labels. Please try again.\n\nSelect a button to rename.\nPress `cancel` to exit.";
            menu.setState(MenuState.RENAME_BUTTON).message().buttons(true).cancel(true).description(desc).queue(db.getGuild(), user, io);
            return;
        }
        if (!new_label.matches("^[-_ \\p{L}\\p{N}\\p{sc=Deva}\\p{sc=Thai}]{1,32}$")) {
            String desc = "Button labels can only contain alphanumeric characters and spaces, not `" + new_label + "`.\n\nSelect a button to rename.\nPress `cancel` to exit.";
            menu.setState(MenuState.RENAME_BUTTON).message().buttons(true).cancel(true).description(desc).queue(db.getGuild(), user, io);
            return;
        }
        String footer = "";
        if (menu.isDefault()) {
            footer = "\n\nYou may need to restart discord to see changes to context menus";
        }

        String cmd1 = menu.buttons.remove(label);
        menu.buttons.put(new_label, cmd1);
        db.getMenuManager().saveMenu(menu);
        String desc = "Successfully rename `" + label + "` to `" + new_label +
                "`.\n\nSelect a button to rename.\nPress `cancel` to exit." + footer;
        menu.setState(MenuState.RENAME_BUTTON).message().options(true).description(desc).cancel(true).queue(db.getGuild(), user, io);
    }

    @Command(desc = "Create a new custom discord menu set in the guild")
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
        newMenu.message().edit(true).cancel(true).queue(db.getGuild(), user, io);
    }

    @Command(desc = "Cancel the current menu state and return to displaying the menu description and buttons")
    @NoFormat
    @Ephemeral
    public void cancel(@Me IMessageIO io, @Me GuildDB db, @Me User user, AppMenu menu) {
        if (menu.state == MenuState.NONE) {
            menu.clearState().message().buttons(true).edit(menu.canEdit(db, user)).queue(db.getGuild(), user, io);
        } else {
            menu.clearState().message().options(true).cancel(true).queue(db.getGuild(), user, io);
        }
    }

    @Command(desc = "List all custom discord menus set in the guild", viewable = true)
    @NoFormat
    @Ephemeral
    public String list(@Me GuildDB db) {
        Map<String, AppMenu> menus = db.getMenuManager().getAppMenus();
        StringBuilder sb = new StringBuilder();
        for (AppMenu menu : menus.values()) {
            sb.append("### " + menu.title).append("\n");
            String[] descLines = menu.description.split("\n");
            String desc = descLines[0] + (descLines.length > 1 ? "..." : "");
            sb.append("> " + desc).append("\n");
        }
        sb.append("\nSee: " + CM.menu.info.cmd.toSlashMention() + " for more details");
        return sb.toString();
    }

    @Command(desc = "Display the information for a custom discord menu set in the guild", viewable = true)
    @NoFormat
    @Ephemeral
    public void info(@Me IMessageIO io, AppMenu menu) {
        String title = "Menu: " + menu.title;
        StringBuilder body = new StringBuilder();
        body.append("Description:\n```\n").append(menu.description).append("\n```\n");
        body.append("Buttons:\n```\n");
        for (Map.Entry<String, String> entry : menu.buttons.entrySet()) {
            body.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        body.append("```");
        io.create().embed(title, body.toString()).send();
    }
}
