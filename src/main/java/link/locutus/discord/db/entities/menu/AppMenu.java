package link.locutus.discord.db.entities.menu;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
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

    // {user}
    // {message}
    public long targetUser;
    public String targetMessage;
    public String targetContent;

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
                msg.description("Select `Add button`, then enter the button label.").options(true).cancel(true);
            }
            case REMOVE_BUTTON -> {
                msg.description("Select a button to remove.").buttons(true).cancel(true);
            }
            case RENAME_BUTTON -> {
                msg.description("Select a button to rename.").buttons(true).cancel(true);
            }
        }
        return msg.queue(db.getGuild(), user, io);
    }

    public String formatCommand(Guild guild, User user, String cmd) {
        if (cmd.startsWith("{") && cmd.endsWith("}")) {
            try {
                Map<String, String> map = new Object2ObjectLinkedOpenHashMap<>(WebUtil.GSON.fromJson(cmd, Map.class));
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    String value = entry.getValue();
                    if (!value.contains("{") || !value.contains("}")) continue;
                    String newValue = formatValue(guild, user, value);
                    if (newValue != null) {
                        entry.setValue(newValue);
                    }
                }
                return WebUtil.GSON.toJson(map);
            } catch (Exception ignore) {}
        }
        if (!cmd.contains("{") || !cmd.contains("}")) return cmd;
        String newValue = formatValue(guild, user, cmd);
        return newValue != null ? newValue : cmd;
    }

    private String formatValue(Guild guild, User user, String value) {
        boolean replaced = false;
        if (targetUser != 0 && value.contains("{user}")) {
            value = value.replace("{user}", "<@" + targetUser + ">");
            replaced = true;
        }
        if (targetMessage != null && value.contains("{message}")) {
            value = value.replace("{message}", targetMessage);
            replaced = true;
        }
        if (targetContent != null && value.contains("{content}")) {
            value = value.replace("{content}", targetContent);
            replaced = true;
        }
        long natUser = targetUser == 0 && user != null ? user.getIdLong() : targetUser;
        if (natUser != 0 && value.contains("{") && value.contains("}")) {
            DBNation nation = DiscordUtil.getNation(natUser);
            if (nation != null) {
                value = Locutus.cmd().getV2().getNationPlaceholders().format2(guild, null, user, value, nation, false);
            }
        }
        return replaced ? value : null;
    }

    public boolean isDefault() {
        return title.equalsIgnoreCase("user") || title.equalsIgnoreCase("message");
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

        public CompletableFuture<IMessageBuilder> queue(Guild guild, User user, IMessageIO io) {
            String desc = overrideDescription != null ? overrideDescription : description;
            IMessageBuilder msg = io.create().embed(title, desc);
            if (showButtons) {
                switch (state) {
                    default -> {
                        for (Map.Entry<String, String> entry : buttons.entrySet()) {
                            String label = entry.getKey();
                            String command = formatCommand(guild, user, entry.getValue());
                            msg.commandButton(CommandBehavior.EPHEMERAL, command, label);
                        }
                        break;
                    }
                    case REORDER -> {
                        CM.menu.button.swap cmd = CM.menu.button.swap.cmd;
                        for (Map.Entry<String, String> entry : buttons.entrySet()) {
                            String label1 = entry.getKey();
                            cmd = cmd.menu(title).label1(label1);
                            if (lastPressedButton != null) {
                                cmd = cmd.label2(lastPressedButton);
                            }
                            msg.commandButton(CommandBehavior.EPHEMERAL, cmd, label1);
                        }
                        break;
                    }
                    case RENAME_BUTTON -> {
                        CM.menu.button.rename cmd = CM.menu.button.rename.cmd;
                        for (Map.Entry<String, String> entry : buttons.entrySet()) {
                            String label1 = entry.getKey();
                            cmd = cmd.menu(title).label(label1).new_label("");
                            msg.modal(CommandBehavior.EPHEMERAL, cmd, label1);
                        }
                        break;
                    }
                    case REMOVE_BUTTON -> {
                        CM.menu.button.remove cmd = CM.menu.button.remove.cmd;
                        for (Map.Entry<String, String> entry : buttons.entrySet()) {
                            String label1 = entry.getKey();
                            cmd = cmd.menu(title).label(label1);
                            msg.commandButton(CommandBehavior.EPHEMERAL, cmd, label1);
                        }
                        break;
                    }
                }
            }
            if (showEditButton) {
                msg.commandButton(CommandBehavior.EPHEMERAL, CM.menu.edit.cmd.menu(title), "Edit");
            }
            if (showEditOptions) {
                CommandRef renameModel = CM.menu.title.cmd.menu(title).name("");
                CommandRef describeModel = CM.menu.description.cmd.menu(title).description("");
                CommandRef reorderCommand = CM.menu.context.cmd.menu(title).state(MenuState.REORDER.name());
                CommandRef addButtonModal = CM.menu.button.add.cmd.menu(title).label("");
                CommandRef removeButtonCommand = CM.menu.context.cmd.menu(title).state(MenuState.REMOVE_BUTTON.name());
                CommandRef renameButtonCommand = CM.menu.context.cmd.menu(title).state(MenuState.RENAME_BUTTON.name());
                CommandRef deleteMenu = CM.menu.delete.cmd.menu(title);

                msg.modal(CommandBehavior.EPHEMERAL, renameModel, "Rename Menu")
                   .modal(CommandBehavior.EPHEMERAL, describeModel, "Menu Description")
                   .commandButton(CommandBehavior.EPHEMERAL, reorderCommand, "Reorder Buttons")
                   .modal(CommandBehavior.EPHEMERAL, addButtonModal, "Add Button")
                   .commandButton(CommandBehavior.EPHEMERAL, removeButtonCommand, "Remove Button")
                   .commandButton(CommandBehavior.EPHEMERAL, renameButtonCommand, "Rename Button")
                   .commandButton(CommandBehavior.EPHEMERAL, deleteMenu, "Delete Menu");
            }
            if (showCancelButton) {
                CM.menu.cancel cmd = CM.menu.cancel.cmd.menu(title);
                msg.commandButton(CommandBehavior.EPHEMERAL, cmd, "Cancel");
            }
            io.setMessageDeleted();
            return msg.send();
        }
    }
}
