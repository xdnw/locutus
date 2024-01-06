package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.bindings.Operation;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.GoogleDoc;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.json.JSONObject;
import retrofit2.http.HEAD;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EmbedCommands {
    @Command(desc = "Create a simple embed with a title and description")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public void create(@Me IMessageIO io, String title, String description) {
        io.create().embed(title, description.replace("\\n", "\n")).send();
    }

    private void checkMessagePerms(User user, Guild guild, Message message) {
        if (!message.isFromGuild()) {
            throw new IllegalArgumentException("Embeds can only be edited in a guild");
        }
        if (message.getGuild().getIdLong() != guild.getIdLong() && !Roles.INTERNAL_AFFAIRS.has(user, message.getGuild())) {
            throw new IllegalArgumentException("You can only edit embeds in your own guild or one you have `" + Roles.INTERNAL_AFFAIRS.name() + "` in");
        }
    }


    @Command(desc = "Set the title of an embed from this bot")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String title(@Me User user, @Me Guild guild, Message discMessage, String title) {
        checkMessagePerms(user, guild, discMessage);
        DiscordMessageBuilder message = new DiscordMessageBuilder(discMessage.getChannel(), discMessage);
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        MessageEmbed embed = embeds.get(0);

        EmbedBuilder builder = new EmbedBuilder(embed);
        builder.setTitle(title);

        message.clearEmbeds();
        message.embed(builder.build());
        message.send();
        return "Done! See: " + discMessage.getJumpUrl();
    }

    @Command(desc = "Set the description of an embed from this bot")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String description(@Me User user, @Me Guild guild, Message discMessage, String description) {
        checkMessagePerms(user, guild, discMessage);
        DiscordMessageBuilder message = new DiscordMessageBuilder(discMessage.getChannel(), discMessage);
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        MessageEmbed embed = embeds.get(0);

        EmbedBuilder builder = new EmbedBuilder(embed);
        builder.setDescription(description.replace("\\n", "\n"));

        message.clearEmbeds();
        message.embed(builder.build());
        message.send();
        return "Done! See: " + discMessage.getJumpUrl();
    }

    @Command(desc = "Remove a button from an embed from this bot")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String removeButton(@Me User user, @Me Guild guild, Message message, @Arg("A comma separated list of button labels") @TextArea(',') List<String> labels) {
        checkMessagePerms(user, guild, message);
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            throw new IllegalArgumentException("The message you linked is not from the bot. Only bot messages can be modified.");
        }
        Set<String> labelSet = Set.copyOf(labels);
        List<Button> buttons = message.getButtons();

        Set<String> invalidLabels = new HashSet<>(labels);
        Set<String> validLabels = new LinkedHashSet<>();
        List<ActionRow> rows = new ArrayList<>(message.getActionRows());
        for (int i = 0; i < rows.size(); i++) {
            ActionRow row = rows.get(i);
            List<ItemComponent> components = new ArrayList<>(row.getComponents());
            components.stream().filter(f -> f instanceof Button).map(f -> ((Button) f).getLabel()).forEach(validLabels::add);
            components.removeIf(f -> {
                if (f instanceof Button button) {
                    if (labelSet.contains(button.getLabel())) {
                        invalidLabels.remove(button.getLabel());
                        return true;
                    }
                }
                return false;
            });
            rows.set(i, ActionRow.of(components));
        }
        if (!invalidLabels.isEmpty()) {
            throw new IllegalArgumentException("Invalid labels: `" + StringMan.join(invalidLabels, ", ") + "`. Valid labels: `" + StringMan.join(validLabels, ", ") + "`");
        }

        RateLimitUtil.queue(message.editMessageComponents(rows));
        return "Done! Deleted " + labels.size() + " buttons";
    }

    @Command(desc = "Add a button to a discord embed from this bot which runs a command\n" +
            "Supports legacy commands and user command syntax.\n" +
            "Unlike `embed add button`, this does not parse and validate command input.")
    @NoFormat
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String addButtonRaw(@Me User user, @Me IMessageIO io, @Me JSONObject cmdJson,  @Me Guild guild, Message message, String label, CommandBehavior behavior, String command, @Switch("c") MessageChannel channel, @Switch("f") boolean force) {
        checkMessagePerms(user, guild, message);
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            throw new IllegalArgumentException("The message you linked is not from the bot. Only bot messages can be modified.");
        }
        if (!label.matches("[a-zA-Z0-9_ ]+")) {
            throw new IllegalArgumentException("Label must be alphanumeric, not: `" + label + "`");
        }

        List<Button> buttons = message.getButtons();
        if (!force) {
            for (Button button : buttons) {
                if (button.getLabel().equalsIgnoreCase(label)) {
                    String title = "Button already exists";
                    String body = "The button label `" + label + "` already exists on the embed.\n\n" +
                            "Would you like to replace it?";
                    io.create().confirmation(title, body, cmdJson).send();
                    return null;
                }
            }
        }
        if (buttons.size() >= 25) {
            throw new IllegalArgumentException("You cannot have more than 25 buttons on an embed. Please remove one first: " + CM.embed.remove.button.cmd.toSlashMention());
        }

        Long channelId = channel == null ? null : channel.getIdLong();
        new DiscordMessageBuilder(message.getChannel(), message)
                .removeButtonByLabel(label)
                .commandButton(behavior, channelId, command.replace("\\n", "\n"), label)
                .send();
        return "Done! Added button `" + label + "` to " + message.getJumpUrl() + "\n" +
                "Remove it using: " + CM.embed.remove.button.cmd.toSlashMention();
    }

    @Command(desc = "Add a button to a discord embed from this bot which runs a command")
    @NoFormat
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String addButton(@Me User user, @Me IMessageIO io, @Me JSONObject cmdJson, @Me Guild guild, Message message, String label, CommandBehavior behavior, ICommand command,
                            @Default @Arg("The arguments and values you want to submit to the command\n" +
                                    "Example: `myarg1:myvalue1 myarg2:myvalue2`\n" +
                                    "For placeholders: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                            String arguments, @Switch("c") MessageChannel channel, @Switch("f") boolean force) {
        checkMessagePerms(user, guild, message);
        Set<String> validArguments = command.getUserParameterMap().keySet();

        Map<String, String> parsed = arguments == null ? new HashMap<>() : CommandManager2.parseArguments(validArguments, arguments, true);
        // ensure required arguments aren't missing
        for (ParameterData param : command.getUserParameters()) {
            if (param.isOptional() || param.isFlag()) continue;
            String name = param.getName();
            if (!parsed.containsKey(name) && !parsed.containsKey(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("The command `" + command.getFullPath() + "` has a required argument `" + name + "` that is missing from your `arguments` value: `" + arguments + "`.\n" +
                        "See: " + CM.help.command.cmd.create(command.getFullPath()));
            }
        }

        String commandStr =  command.toCommandArgs(parsed);
        return addButtonRaw(user, io, cmdJson, guild, message, label, behavior, commandStr, channel, force);
    }

    @Command(desc = "Add a modal button to a discord embed from this bot, which creates a prompt for a command")
    @NoFormat
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String addModal(@Me User user, @Me Guild guild, Message message, String label, CommandBehavior behavior, ICommand command,
                           @Arg("A comma separated list of the command arguments to prompt for\n" +
                                   "Arguments can be one of the named arguments for the command, or the name of any `{placeholder}` you have for `defaults`") String arguments,
                           @Arg("The default arguments and values you want to submit to the command\n" +
                                   "Example: `myarg1:myvalue1 myarg2:myvalue2`\n" +
                                   "For placeholders: <https://github.com/xdnw/locutus/wiki/nation_placeholders>")
                           @Default String defaults, @Switch("c") MessageChannel channel) {
        checkMessagePerms(user, guild, message);
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            throw new IllegalArgumentException("The message you linked is not from the bot. Only bot messages can be modified.");
        }
        if (!label.matches("[a-zA-Z0-9_ ]+")) {
            throw new IllegalArgumentException("Label must be alphanumeric, not: `" + label + "`");
        }
        Set<String> validArguments = command.getUserParameterMap().keySet();

        List<Button> buttons = message.getButtons();
        for (Button button : buttons) {
            if (button.getLabel().equalsIgnoreCase(label)) {
                throw new IllegalArgumentException("The button label `" + label + "` already exists on the embed. Please remove it first: " + CM.embed.remove.button.cmd.toSlashMention());
            }
        }
        if (buttons.size() >= 25) {
            throw new IllegalArgumentException("You cannot have more than 25 buttons on an embed. Please remove one first: " + CM.embed.remove.button.cmd.toSlashMention());
        }
        Set<String> promptedArguments = new HashSet<>(StringMan.split(arguments, ','));
        Map<String, String> providedArguments = defaults == null ? new HashMap<>() : CommandManager2.parseArguments(validArguments, defaults, true);

        for (String arg : promptedArguments) {
            String argLower = arg.toLowerCase(Locale.ROOT);
            if (defaults.contains("{" + argLower)) continue;

            if (!validArguments.contains(arg) && !validArguments.contains(argLower)) {
                throw new IllegalArgumentException("The command `" + command.getFullPath() + "` does not have an argument `" + arg + "`. Valid arguments: `" + StringMan.getString(validArguments) + "`");
            }
            if (providedArguments.containsKey(arg) || providedArguments.containsKey(argLower)) {
                throw new IllegalArgumentException("You have specified the argument `" + arg + "` in both `arguments` and `defaults`. Please only specify it in one.");
            }
        }

        for (ParameterData param : command.getUserParameters()) {
            if (param.isOptional() || param.isFlag()) continue;
            String name = param.getName();
            String nameL = name.toLowerCase(Locale.ROOT);
            if (!promptedArguments.contains(name) && !promptedArguments.contains(nameL) && !providedArguments.containsKey(name) && !providedArguments.containsKey(nameL)) {
                throw new IllegalArgumentException("The command `" + command.getFullPath() + "` has a required argument `" + name + "` that is missing from your `arguments` or `defaults`.\n" +
                        "See: " + CM.help.command.cmd.create(command.getFullPath()));
            }
        }

        Map<String, String> full = new LinkedHashMap<>(providedArguments);
        for (String arg : promptedArguments) {
            full.put(arg, "");
        }

        Long channelId = channel == null ? null : channel.getIdLong();
        new DiscordMessageBuilder(message.getChannel(), message)
                .modal(behavior, channelId, command, full, label)
                .send();
        return "Done! Added modal button `" + label + "` to " + message.getJumpUrl();
    }


//    @Command(desc = "Add a command button to an embed")
//    @RolePermission(Roles.INTERNAL_AFFAIRS)
//    public String addCommand(Message message, String label, CommandRef command, @Default CommandBehavior behavior, @Default TextChannel output) {
//
//    }

    /*
    @Loto !embed "Set Brackets" "0 = Set yourself to 25/25
1 = Set yourself to 90/90
2 = Set yourself to 100/100
3 = Set all unblockaded 25/25 nations to 90/90 (Gov)" "~!SetBracket {nation_id} 25/25" "~!SetBracket {nation_id} 90/90" "~!SetBracket {nation_id} 100/100" "~$nation set taxbracket tax_id=19107,tax_id=19091,#isblockaded=0 90/90"
     */

    /*
    !embed "Set your required status for beige alerts" "0 = online
1 = Online or Away
2 = Online, Away or DoNotDisturb
3 = Any status
>results in #🤖│war-bot" "#🤖│war-bot ~$beigeAlertRequiredStatus ONLINE" "#🤖│war-bot ~$beigeAlertRequiredStatus ONLINE_AWAY" "#🤖│war-bot ~$beigeAlertRequiredStatus ONLINE_AWAY_DND" "~$beigeAlertRequiredStatus ANY"
     */

    /*
    !embed "Econ Shortcuts" "0 = Check deposits
1 = Nation checkup
2 = Check ROI
3 = Disburse funds for 3 days
`Open a grant request if you are unable to disburse`
#grant-requests

Results in #econ-bot" "#econ-bot ~!depo %user%" "#econ-bot ~!checkup %user%" "#econ-bot ~!ROI %user% 120" "#econ-bot ~!disburse %user% 3 -f"
     */

    /*
    !embed "Request a grant or loan" "0 = Project grant
1 = Infra/Land/Building grant
2 = City grant
3 = Reimburse deposits after a war
4 = Warchest (before fighting a war)
5 = **REBUILD GRANT**
6 = Other" "~!channel project-{nation_id} grants-2,grants-3 project_grant -a -e -p" "~!channel build-{nation_id} grants-2,grants-3 build_grant -a -e -p" "~!channel city-{nation_id} grants-2,grants-3 city_grant -a -e -p" "~!channel reimburse-{nation_id} grants-2,grants-3 reimburse_grant -a -e -p" "~!channel warchest-{nation_id} grants-2,grants-3 warchest_grant -a -e -p" "~!channel other-{nation_id} grants-2,grants-3 rebuild -a -e -p" "~!channel other-{nation_id} grants-2,grants-3 other_grant -a -e -p"
     */

    /*
    !embed "Find targets" "**------ RAID TARGETS ------**
Click :zero: for safe inactive nones/apps
Click :one: to include inactives in alliances
Click :two: to include nations on beige
Click :three: to include actives with minimal ground
Click :four: to include actives with minimal ground (2d inactive)
Click :five: to include actives losing their current wars

**------ Attrition/War Targets ------**
Click :six: for **high infra** (attrition) targets
Click :seven:  for standard war targets

*ping a gov member if you'd like any help/advice
Results are sorted best to last in #:robot:│war-bot" "#:robot:│war-bot ~!raid 10" "#:robot:│war-bot ~!raid * 25" "#:robot:│war-bot ~!raid * 25 -beige<24" "#:robot:│war-bot ~!raid #tankpct<20,#soldier%<40,* 25 -a -w" "#:robot:│war-bot ~!raid #tankpct<20,#soldier%<40,* 25 -2d -w" "#:robot:│war-bot ~!raid #def>0,#strength<1,* 25 -a -w" "#:robot:│war-bot ~!damage ~enemies" "#:robot:│war-bot ~!war ~enemies"
     */

    // TODO disburse to tax brackets

    /*
    !embed "Find targets" "**------ RAID TARGETS ------**
Click  for safe inactive nones/apps
Click  to include inactives in alliances
Click  to include nations on beige
Click  to include actives with minimal ground
Click  to include actives with minimal ground (2d inactive)
Click  to include actives losing their current wars
Click  to find unprotected nations (be careful with this)

*ping a gov member if you'd like any help/advice
Results are sorted best to last in <#995168236213633024>" "<#995168236213633024> ~!raid 10" "<#995168236213633024> ~!raid * 25" "<#995168236213633024> ~!raid * 25 -beige<24" "<#995168236213633024> ~!raid #tankpct<20,#soldier%<40,* 25 -a -w" "<#995168236213633024> ~!raid #tankpct<20,#soldier%<40,* 25 -2d -w" "<#995168236213633024> ~!raid #def>0,#strength<1,* 25 -a -w" "<#995168236213633024> ~$unprotected * -a -c 90"
     */
    @Command(desc="Makes a raid panel, which is a discord embed with buttons for different options for finding raid targets")
    @RolePermission(Roles.ADMIN)
    public void raid(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        String title = "Find Raid Targets";
        String body = """
                        Press `7d_app` for inactive nones/apps
                        Press `7d_members` for inactives in alliances
                        Press `7d_beige` for nations on beige
                        Press `ground` for actives with low ground
                        Press `2d_ground` for 2d inactives with low ground
                        Press `losing` for actives losing their current wars
                        Press `unprotected` for with weak or no available counters
                        """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CM.war.find.raid app = CM.war.find.raid.cmd.create(
                null, "10", null, null, null, null, null, null, null, null, null);
        CM.war.find.raid members = CM.war.find.raid.cmd.create(
                "*", "25", null, null, null, null, null, null, null, null, null);
        CM.war.find.raid beige = CM.war.find.raid.cmd.create(
                "*", "25", null, null, "24", null, null, null, null, null, null);
        CM.war.find.raid ground = CM.war.find.raid.cmd.create(
                "#tankpct<20,#soldier%<40,*", "25", "0d", "true", null, null, null, null, null, null, null);
        CM.war.find.raid ground_2d = CM.war.find.raid.cmd.create(
                "#tankpct<20,#soldier%<40,*", "25", "2d",  "true", null,null, null, null, null, null, null);
        CM.war.find.raid losing = CM.war.find.raid.cmd.create(
                "#def>0,#RelativeStrength<1,*", "25", "0d", "true", null, null, null, null, null, null, null);
        CM.war.find.unprotected unprotected = CM.war.find.unprotected.cmd.create(
                "*", "25", null, "true", null,  null, "90", null, null, null);

        CommandBehavior behavior = CommandBehavior.UNPRESS;
        io.create().embed(title, body)
                .commandButton(behavior, channelId, app, "7d_app")
                .commandButton(behavior, channelId, members, "7d_members")
                .commandButton(behavior, channelId, beige, "7d_beige")
                .commandButton(behavior, channelId, ground, "ground")
                .commandButton(behavior, channelId, ground_2d, "2d_ground")
                .commandButton(behavior, channelId, losing, "losing")
                .commandButton(behavior, channelId, unprotected, "unprotected")
                .send();
    }

        /*
    @Locutus#7602 !embed "Blockade Target & Requests" "**Request your blockade broken**
See e.g.: `/war blockade request diff: 3d note: some reason`
0 = Low on resources
1 = Need to deposit
2 = Broke
---
3 = Find enemies w/ blockades
3 = Find enemies w/ blockades on unpowered allies
See e.g: `/war blockade find allies: ~allies numships: 250`

>results in #milcom-bot"
"#milcom-bot ~$war blockade request 3d 'Low on resources'"
"#milcom-bot ~$war blockade request 3d 'need to deposit'"
"#milcom-bot ~$war blockade request 3d 'broke'"
"#milcom-bot ~$war blockade find ~allies,#active_m<2880 -r 10"
"#milcom-bot ~$war blockade find ~allies,#ispowered=0,#active_m<2880 -r 10"
     */
        @Command(desc="Blockader Target & Requests discord embed template")
        @RolePermission(Roles.ADMIN)
        public void unblockadeRequests(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
            if (db.getCoalition(Coalition.ALLIES).isEmpty()) {
                throw new IllegalArgumentException("No `" + Coalition.ALLIES.name() + "` coalition. See " + CM.coalition.create.cmd.toSlashMention());
            }
            db.getOrThrow(GuildKey.UNBLOCKADE_REQUESTS);
            db.getOrThrow(GuildKey.UNBLOCKADED_ALERTS);
            if (Roles.UNBLOCKADED_ALERT.toRole(db) == null) {
                throw new IllegalArgumentException(Roles.UNBLOCKADED_ALERT.toDiscordRoleNameElseInstructions(db.getGuild()));
            }

            Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
            String title = "Blockade Target & Requests";
            String body = """
                    **Request your blockade broken**
                    See e.g.: """ + CM.war.blockade.request.cmd.create("3d", "some reason", null).toSlashCommand(true)+ """
                    Press `Low` if low on resources
                    Press `deposit` if you need to deposit
                    Press `broke` if you are out of resources
                    ---
                    Press `break` to find enemies w/ blockades
                    Press `unpowered` to find enemies w/ blockades on unpowered allies
                    """;
            body += "\nSee e.g: " + CM.war.blockade.find.cmd.create("~allies", null, "250", null).toSlashCommand();

            if (channelId != null) {
                body += "\n\n> Results in <#" + channelId + ">";
            }

            CM.war.blockade.request low = CM.war.blockade.request.cmd.create("3d", "Low on resources", null);
            CM.war.blockade.request deposit = CM.war.blockade.request.cmd.create("3d", "Need to deposit", null);
            CM.war.blockade.request broke = CM.war.blockade.request.cmd.create("3d", "Broke", null);
            CM.war.blockade.find breakCmd = CM.war.blockade.find.cmd.create("~allies,#active_m<2880", null, null, "10");
            CM.war.blockade.find breakUnpowered = CM.war.blockade.find.cmd.create("~allies,#ispowered=0,#active_m<2880", null, null, "10");

            CommandBehavior behavior = CommandBehavior.UNPRESS;
            io.create().embed(title, body)
                    .commandButton(behavior, channelId, low, "low")
                    .commandButton(behavior, channelId, deposit, "deposit")
                    .commandButton(behavior, channelId, broke, "broke")
                    .commandButton(behavior, channelId, breakCmd, "break")
                    .commandButton(behavior, channelId, breakUnpowered, "unpowered")
                    .send();
        }

        @Command(desc="Econ panel for members")
        @RolePermission(Roles.ADMIN)
        public void memberEconPanel(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel, @Switch("d") boolean showDepositsInDms) {
            Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
            String title = "Econ Panel";
            String body = """
                    Press `offshore` to send funds offshore
                    Press `balance` to view your deposits
                    Press `breakdown` to view your deposits breakdown
                    Press `tax` to view your tax rate 
                    Press `revenue` to check your revenue
                    Press `optimal` to optimize your build (same mmr/infra)
                    Press `price` to check the trade price
                    Press `margin` to check the trade margin
                    Press `profit` to check your trade profit
                    """;

            if (channelId != null) {
                body += "\n\n> Results in <#" + channelId + ">";
            }

            CM.offshore.send send = CM.offshore.send.cmd.create(null, null, null);
            CM.deposits.check deposits = CM.deposits.check.cmd.create("{nation_id}", null, null, null, null, null, showDepositsInDms + "", null, null, null, null);
            CM.deposits.check depositsBreakdown = CM.deposits.check.cmd.create("{nation_id}", null, null, null, null, "true", null, null, null, null, null);
            CM.tax.info taxInfo = CM.tax.info.cmd.create("{nation_id}");
            CM.nation.revenue revenue = CM.nation.revenue.cmd.create("{nation_id}", "true", null, null, null, null);
            CM.city.optimalBuild optimalbuild = CM.city.optimalBuild.cmd.create("{city 1}", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            CM.trade.price tradeprice = CM.trade.price.cmd.create();
            CM.trade.margin trademargin = CM.trade.margin.cmd.create(null);
            CM.trade.profit tradeprofit = CM.trade.profit.cmd.create("{nation_id}", "7d");

            CommandBehavior behavior = CommandBehavior.UNPRESS;
            io.create().embed(title, body)
                    .commandButton(behavior, channelId, send, "offshore")
                    .commandButton(behavior, channelId, deposits, "balance")
                    .commandButton(behavior, channelId, depositsBreakdown, "breakdown")
                    .commandButton(behavior, channelId, taxInfo, "tax_info")
                    .commandButton(behavior, channelId, revenue, "revenue")
                    .commandButton(behavior, channelId, optimalbuild, "optimal")
                    .commandButton(behavior, channelId, tradeprice, "price")
                    .commandButton(behavior, channelId, trademargin, "margin")
                    .commandButton(behavior, channelId, tradeprofit, "profit")
                    .send();

        }




//    Spy embed with spy - airplane - tank - ship spying
//    Winning target being high average infra, high mil, low mil, off beige soon
//
//    @Command(desc="Enemy war targets when it is even and unknowable\n" +
//            "Prioritizes overextended enemies\n" +
//            "To find contestable range, see: strengthTierGraph")
//    @RolePermission(Roles.ADMIN)
//    public void warEqual(@Me User user, @Me GuildDB db, @Me IMessageIO io,
//                         @Arg("If the cutoff is greater or less than the score") Operation greaterOrLess,
//                         @Arg("The score at which the conflict is not contestable")
//                         double score, @Default MessageChannel outputChannel, @Switch("d") boolean resultsInDm) {
//
//    }
//


    /*
    @Locutus#7602 !embed "High infra enemies" "0 = Active enemies
1 = Weak enemies
2 = No navy
3 = no vital defense system
4 = no iron dome
5 = no VDS or ID" "~$war find damage ~enemies"
"~$war find damage ~enemies -w -i -a"
"~$war find damage ~enemies -n"
"~$war find damage ~enemies,#vds=0"
"~$war find damage ~enemies,#id=0"
"~$war find damage ~enemies,#id=0,#vds=0"
     */
    @Command(desc="High infra targets where you are losing\n" +
            "To find contestable range, see: strengthTierGraph")
    @RolePermission(Roles.ADMIN)
    public void warGuerilla(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ENEMIES.name() + "` coalition. See " + CM.coalition.create.cmd.toSlashMention());
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String title = "High infra enemies";
        String body = """
                Press `active` for active enemies
                Press `weak` for weak enemies
                Press `no_navy` for enemies w/o navy
                Press `no_vds` for enemies w/o vital defense system
                Press `no_id` for enemies w/o iron dome
                Press `no_vds_id` for enemies w/o vds & id
                """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CommandBehavior behavior = CommandBehavior.UNPRESS;

        CM.war.find.damage damage = CM.war.find.damage.cmd.create(
                "~enemies", null, null, null, null, null, null, null, null, null, null);
        CM.war.find.damage damageWeak = CM.war.find.damage.cmd.create(
                "~enemies", "true", "true", "true", null, null, null, null, null, null, null);
        CM.war.find.damage damageNoNavy = CM.war.find.damage.cmd.create(
                "~enemies", null, null, null, "true", null, null, null, null, null, null);
        CM.war.find.damage damageNoVDS = CM.war.find.damage.cmd.create(
                "~enemies,#hasProject(vital_defense_system)=0", null, null, null, null, null, null, null, null, null, null);
        CM.war.find.damage damageNoID = CM.war.find.damage.cmd.create(
                "~enemies,#hasProject(iron_dome)=0", null, null, null, null, null, null, null, null, null, null);
        CM.war.find.damage damageNoVDSID = CM.war.find.damage.cmd.create(
                "~enemies,#hasProject(iron_dome)=0,#hasProject(vital_defense_system)=0", null, null, null, null, null, null, null, null, null, null);

        io.create().embed(title, body)
                .commandButton(behavior, channelId, damage, "active")
                .commandButton(behavior, channelId, damageWeak, "weak")
                .commandButton(behavior, channelId, damageNoNavy, "no_navy")
                .commandButton(behavior, channelId, damageNoVDS, "no_vds")
                .commandButton(behavior, channelId, damageNoID, "no_id")
                .commandButton(behavior, channelId, damageNoVDSID, "no_vds_id")
                .send();
    }

            /*
            @Locutus#7602 !embed "War Finder" "**If you are above 5,500 score, ONLY go for easy targets**
(Or if you have defensive wars, and dont want to over extend)
0 = Easy enemies
**If you are below 5.5k score, go for priority targets**
2 = High priority
3 = Lower priority
**If you are weak, and want to stat pad**
4 = Weak enemies
5 = High infra enemies

>results in #raid-results" "#raid-results ~!war ~enemies,#off>0 -e" "#raid-results ~!war ~enemies,#off>0,#score<7333,#attackingenemyofscore<5500,#attacking3/4strengthenemyofscore<5500 -p" "#raid-results ~!war ~enemies,#off>0,#score<7333,#attackingenemyofscore<5500,#attacking3/4strengthenemyofscore<5500 -e -p" "#raid-results ~!war ~enemies -i -a -e -f" "#raid-results ~!damage ~enemies,#active_m>2880||~enemies,#score>7333||~enemies,#barracks=0,#off=0 -i -a"
             */
    @Command(desc="Enemy war targets where a score range is not contestable\n" +
            "To find contestable range, see: strengthTierGraph")
    @RolePermission(Roles.ADMIN)
    public void warContestedRange(@Me User user, @Me GuildDB db, @Me IMessageIO io,
                                  @Arg("If the cutoff is greater or less than the score") Operation greaterOrLess,
                                  @Arg("The score at which the conflict is not contestable")
                                  double score, @Default MessageChannel outputChannel, @Switch("d") boolean resultsInDm) {
        if (greaterOrLess == Operation.EQUAL || greaterOrLess == Operation.NOT_EQUAL) {
            if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
                throw new IllegalArgumentException("No " + Coalition.ENEMIES.name() + " coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
            }
            throw new IllegalArgumentException("Cannot use " + greaterOrLess + " in this command");
        }
        if (resultsInDm && outputChannel != null) {
            throw new IllegalArgumentException("Cannot specify both a channel and DM results");
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        String title = "War Finder";
        String body = String.format("""
                **If you are %1$s %2$s score, ONLY go for easy targets**
                (Or if you have defensive wars, and dont want to over extend)
                Press `easy` for weaker enemies
                
                **If you are %1$s %2$s, go for priority targets**
                Press `high` for high priority enemies
                Press `low` for lower priority enemies
                
                **If you are weak or have no targets and want to stat pad**
                Press `weak` for weak enemies
                Press `infra` for high infra enemies
                """, greaterOrLess.toString(), MathMan.format(score));

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }
        if (resultsInDm) {
            body += "\n\n> Results in DM";
        }

        Operation opposite = greaterOrLess.opposite();
        boolean greater = greaterOrLess == Operation.GREATER || greaterOrLess == Operation.GREATER_EQUAL;
        double minScore = greater ? score : 0;
        double maxScore = greater ? Integer.MAX_VALUE : score;
        String rangeStr = String.format("%.2f", minScore) + "," + String.format("%.2f", maxScore);

        String dmStr = resultsInDm ? "true" : null;
        CM.war.find.enemy easy = CM.war.find.enemy.cmd.create(
                "~enemies,#off>0", null, null, null, null, null, null, "true", null, dmStr, null);
        int scoreMax;
        if (greaterOrLess == Operation.GREATER || greaterOrLess == Operation.GREATER_EQUAL) {
            scoreMax = (int) Math.ceil(score / 0.75);
        } else {
            scoreMax = (int) Math.ceil(score * 0.75);
        }
        int scoreInt = (int) score;
        CM.war.find.enemy high = CM.war.find.enemy.cmd.create(
                "~enemies,#off>0,#score" + opposite + scoreMax + ",#strongestEnemyOfScore" + rangeStr + "<1,#strongestEnemyOfScore" + rangeStr + ">0.66", null, null, null, null, "true", null, null, null, dmStr, null);
        CM.war.find.enemy low = CM.war.find.enemy.cmd.create(
                "~enemies,#off>0,#score" + opposite + scoreMax + ",#strongestEnemyOfScore" + rangeStr + "<1,#strongestEnemyOfScore" + rangeStr + ">0.66", null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy weak = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, "true", "true", null, null, "true", null, dmStr, "true");
        CM.war.find.damage infra = CM.war.find.damage.cmd.create(
                "~enemies,#active_m>2880|~enemies,#score" + greaterOrLess + scoreMax +"|~enemies,#barracks=0,#off=0", "true", "true", null, null, null, null, null, dmStr, null, null);

        CommandBehavior behavior = CommandBehavior.UNPRESS;
        io.create().embed(title, body)
                .commandButton(behavior, channelId, easy, "easy")
                .commandButton(behavior, channelId, high, "high")
                .commandButton(behavior, channelId, low, "low")
                .commandButton(behavior, channelId, weak, "weak")
                .commandButton(behavior, channelId, infra, "infra")
                .send();
    }

    // Spy embed with spy - airplane - tank - ship spying - auto
  @Command(desc="Enemy espionage finder discord embed template")
  @RolePermission(Roles.ADMIN)
  public void spyEnemy(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default @GuildCoalition String coalition, @Default MessageChannel outputChannel) {
        if (coalition == null) coalition = Coalition.ENEMIES.name();
        if (db.getCoalition(coalition).isEmpty()) {
            throw new IllegalArgumentException("No `" + coalition + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String title = "Espionage Enemies";
        String body = """
                Press `spy` to target spies
                Press `airplane` to target airplanes
                Press `tank` to target tanks
                Press `ship` to target ships
                Press `missile` to target missiles
                Press `nuke` to target nukes
                --------------------
                Press `dmg` to target highest net dmg
                Press `kill` to targets highest kills
                """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

      CM.spy.find.target spy = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.SPIES.name(), null, null, null, null);
        CM.spy.find.target airplane = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.AIRCRAFT.name(), null, null, null, null);
        CM.spy.find.target tank = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.TANKS.name(), null, null, null, null);
        CM.spy.find.target ship = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", SpyCount.Operation.SHIPS.name(), null, null, null, null);
        CM.spy.find.target missile = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<2880", SpyCount.Operation.MISSILE.name(), null, null, null, null);
        CM.spy.find.target nuke = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<2880", SpyCount.Operation.NUKE.name(), null, null, null, null);
        CM.spy.find.target dmg = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", "*", null, null, null, null);
        CM.spy.find.target kill = CM.spy.find.target.cmd.create(
                "~" + coalition + ",#active_m<1440", "*", null, null, "true", null);

        CommandBehavior behavior = CommandBehavior.UNPRESS;

        io.create().embed(title, body)
            .commandButton(behavior, channelId, spy, "spy")
            .commandButton(behavior, channelId, airplane, "airplane")
            .commandButton(behavior, channelId, tank, "tank")
            .commandButton(behavior, channelId, ship, "ship")
            .commandButton(behavior, channelId, missile, "missile")
            .commandButton(behavior, channelId, nuke, "nuke")
            .commandButton(behavior, channelId, dmg, "dmg")
            .commandButton(behavior, channelId, kill, "kill")
            .send();
  }

  // Winning target being high average infra, high mil, low mil, off beige soon

    /*
    high: war find target ~enemies,#attacking=~allies,#attacking2/3strengthenemyofscore<99999 -p -e
    med: war find target ~enemies,#attacking=~allies -p -e
    low: war find target ~enemies -p -e

    easy: war find target ~enemies -e
    inactive: war find target ~enemies -e -i -a
    infra: war find damage ~enemies

    beige: war find ~enemies,#color=beige -i -a -b
     */
    @Command(desc="Enemy war targets when you are winning\n" +
            "Prioritizes down declares\n" +
            "To find contestable range, see: strengthTierGraph")
    @RolePermission(Roles.ADMIN)
    public void warWinning(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel, @Switch("d") boolean resultsInDm) {
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No " + Coalition.ENEMIES.name() + " coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String title = "War Targets";
        String body = """
                Press `high` for high priority enemies
                Press `med` for medium priority enemies
                Press `low` for low priority enemies
                --------------------
                Press `easy` to target easy enemies
                Press `inactive` to include all enemies
                Press `infra` to target high infra enemies
                Press `beige` to target enemies on beige
                """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        String dmStr = resultsInDm ? "true" : null;
        CM.war.find.enemy high = CM.war.find.enemy.cmd.create(
                "~enemies,#fighting(~allies),#getStrongestEnemy()>0.66", null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy med = CM.war.find.enemy.cmd.create(
                "~enemies,#fighting(~allies)", null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy low = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, null, null, "true", null, "true", null, dmStr, null);
        CM.war.find.enemy easy = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, null, null, null, null, "true", null, dmStr, null);
        CM.war.find.enemy inactive = CM.war.find.enemy.cmd.create(
                "~enemies", null, null, "true", "true", null, null, "true", null, dmStr, null);
        CM.war.find.damage infra = CM.war.find.damage.cmd.create(
                "~enemies", "true", "true", null, null, null, null, null, null, dmStr, null);
        CM.war.find.enemy beige = CM.war.find.enemy.cmd.create(
                "~enemies,#color=beige", null, null, "true", "true", null, null, "true", null, dmStr, null);

        CommandBehavior behavior = CommandBehavior.UNPRESS;

        io.create().embed(title, body)
            .commandButton(behavior, channelId, high, "high")
            .commandButton(behavior, channelId, med, "med")
            .commandButton(behavior, channelId, low, "low")
            .commandButton(behavior, channelId, easy, "easy")
            .commandButton(behavior, channelId, inactive, "inactive")
            .commandButton(behavior, channelId, infra, "infra")
            .commandButton(behavior, channelId, beige, "beige")
            .send();
    }

    @Command(desc = "Discord embed for Econ Staff to view deposits, stockpiles, revenue, tax brackets, tax income, warchest and offshore funds")
    @RolePermission(Roles.ADMIN)
    @IsAlliance
    public void econPanel(@Me GuildDB db, @Me IMessageIO io, @Switch("c") MessageChannel outputChannel, @Switch("n") DepositType useFlowNote, @Arg("Include past depositors in deposits sheet") @Switch("p") Set<Integer> includePastDepositors) {
        // useFlowNoteStr
        String useFlowNoteStr = useFlowNote == null ? null : useFlowNote.toString();
        // pastDepositorsStr
        String pastDepositorsStr = includePastDepositors == null ? null : includePastDepositors.stream().map(Object::toString).collect(Collectors.joining(","));

        String title = "Econ Panel 1";
        String body = """
                Press `deposits` to view member deposits sheet
                Press `stockpile` to view member stockpile sheet
                Press `revenue` to view member revenue sheet
                Press `bracket` to view tax bracket sheet
                Press `tax` to view tax revenue sheet
                Press `warchest` to view warchest sheet
                """;

        boolean offshoreBalance = false;
        boolean offshoreSend = false;
        if (db.getOffshoreDB(false) != null) {
            if (db.getOffshoreDB().getKey() == db) {
                body = "Press `offshore` to view offshore account balances\n" + body;
                offshoreBalance = true;
            } else {
                body = "Press `offshore` to offshore alliance funds\n" + body;
                offshoreSend = true;
            }
        }

        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        String allianceStr = db.getAllianceIds().stream().map(f -> "AA:" + f).collect(Collectors.joining(",")) + ",#position>1,#vm_turns=0";

        CommandBehavior behavior = outputChannel == null ? CommandBehavior.EPHEMERAL : CommandBehavior.UNPRESS;

        IMessageBuilder msg = io.create().embed(title, body);
        if (offshoreBalance) {
            msg = msg.commandButton(behavior, channelId, CM.offshore.accountSheet.cmd.create(null), "offshore");
        }
        if (offshoreSend) {
            msg = msg.commandButton(behavior, channelId, CM.offshore.send.cmd.create(null, null, null), "offshore");
        }
        // deposits
        msg = msg.commandButton(behavior, channelId, CM.deposits.sheet.cmd.create(null, null, null, null, null, null, null, null, pastDepositorsStr, null, useFlowNoteStr, null), "deposits");
        // stockpile
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.stockpileSheet.cmd.create(null, null, null, null), "stockpile");
        // revenue
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.revenueSheet.cmd.create(allianceStr, null), "revenue");
        // bracket
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.taxBracketSheet.cmd.create(null, null), "bracket");
        // tax
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.taxRevenue.cmd.create(null, null, null, null), "tax");
        // warchest
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.warchestSheet.cmd.create(allianceStr, null, null, null, null, null, null, null), "warchest");
        msg.send();
    }

    // todo ia panel
    // // Press `audit` to view member audit sheet /audit sheet
    //        // Press `activity` to view member activity sheet /sheets_ia activitysheet
    //        // Press `auto` to run role auto assign task /role autoassign
    // Mail audit results (/audit run
    // /sheets_ia daychange
    // /audit hasNotBoughtSpies
    // /sheets_milcom mmrsheet
    @Command(desc = "Discord embed for Internal Affairs Staff to auto-assign roles and view member activity, audit results, daychange, spy purchase, mmr")
    @RolePermission(Roles.ADMIN)
    @IsAlliance
    public void iaPanel(@Me GuildDB db, @Me IMessageIO io, @Switch("c") MessageChannel outputChannel) {
        String title = "IA Panel";
        String body = """
                Press `audit` to view member audit sheet
                Press `mail` to mail audit results
                Press `activity` to view member activity sheet
                Press `dc` to view member daychange sheet
                Press `spies` to view nations who have not bought spies
                Press `mmr` to view member mmr sheet
                Press `auto` to run role auto assign task
                """;

        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CommandBehavior behavior = outputChannel == null ? CommandBehavior.EPHEMERAL : CommandBehavior.UNPRESS;

        String allianceStr = db.getAllianceIds().stream().map(f -> "AA:" + f).collect(Collectors.joining(",")) + ",#position>1,#vm_turns=0";

        IMessageBuilder msg = io.create().embed(title, body);
        // audit
        msg = msg.commandButton(behavior, channelId, CM.audit.sheet.cmd.create(null, null, null, null, null, null), "audit");
        // mail
        msg = msg.commandButton(behavior, channelId, CM.audit.run.cmd.create(allianceStr, null, null, "true", null, null), "mail");
        // activity
        msg = msg.commandButton(behavior, channelId, CM.sheets_ia.ActivitySheet.cmd.create(allianceStr, null, null), "activity");
        // daychange
        msg = msg.commandButton(behavior, channelId, CM.sheets_ia.daychange.cmd.create(allianceStr, null), "dc");
        // spies
        msg = msg.commandButton(behavior, channelId, CM.audit.hasNotBoughtSpies.cmd.create(allianceStr), "spies");
        // mmr
        msg = msg.commandButton(behavior, channelId, CM.sheets_milcom.MMRSheet.cmd.create(allianceStr, null, null, null), "mmr");
        // auto
        msg = msg.commandButton(behavior, channelId, CM.role.autoassign.cmd.create(null), "auto");

        msg.send();
    }

    @Command(desc = "Discord embed for checking deposits, withdrawing funds, viewing your stockpile, depositing resources and offshoring funds")
    @HasOffshore
    @RolePermission(Roles.ADMIN)
    public void depositsPanel(@Me GuildDB db, @Me IMessageIO io, @Arg("Only applicable to corporate servers. The nation accepting trades for bank deposits. Defaults to the bot owner's nation") @Default DBNation bankerNation, @Switch("c") MessageChannel outputChannel) {
        int nationId = Settings.INSTANCE.NATION_ID;
        if (bankerNation != null) {
            nationId = bankerNation.getId();
        }

        // Add add credentials link to the panel, but not as a command button
        String title = "Member Deposits Panel";
        String body = """
                Press `balance` to view your deposit balance
                Press `self` to withdraw to your own nation
                Press `other` to send your funds to another receiver
                Press `stockpile` to view your stockpile
                
                """;

        boolean isCorp = db.getAllianceIds().isEmpty();

        List<CommandRef> addButtons = new ArrayList<>();
        List<String> addLabels = new ArrayList<>();
        List<Boolean> isModals = new ArrayList<>();

        if (isCorp) {
            body += "\nTo deposit, send a PRIVATE trade offer to " + PnwUtil.getMarkdownUrl(nationId, false) + ":\n" +
                    "- Selling a resource for $0\n" +
                    "- Buying food for OVER $100,000\n" +
                    "Press `trade deposit` if you have sent trades\n" +
                    "Press `trade deposit amount` if you want to create trades for an amount\n";

            addButtons.add(CM.trade.accept.cmd.create(nationId + "", null, null, null));
            addLabels.add("deposit trade");
            isModals.add(false);

            addButtons.add(CM.trade.accept.cmd.create(nationId + "", "", null, null));
            addLabels.add("deposit trade amount");
            isModals.add(false);
        } else {
            body += "\nTo deposit, go to your alliance bank page in-game.\n" +
                    "Alternatively, set your api key with: " + CM.credentials.addApiKey.cmd.toSlashMention() + "\n" +
                    "And then press" +
                    "- `deposit custom` or `deposit auto`" +
                    "- `offshore` to offshore funds";
            addButtons.add(CM.bank.deposit.cmd.create("nation:{nation_id}", null, "", null, null, null, null, null, null, null, null, null, null, null, "true", "true"));
            addLabels.add("deposit custom");
            isModals.add(true);


            CommandRef depositAuto = CM.bank.deposit.cmd.create("nation:{nation_id}", null, null, "7", null, null, "1", null, null, null, null, null, null, null, "true", "true");
            addButtons.add(depositAuto);
            addLabels.add("deposit auto");
            isModals.add(false);
            CommandRef offshore = CM.offshore.send.cmd.create(null, null, null);
            addButtons.add(offshore);
            addLabels.add("offshore");
            isModals.add(false);
        }

        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CM.deposits.check deposits = CM.deposits.check.cmd.create("nation:{nation_id}", null, null, null, null, null, null, null, null, null, null);
        CM.transfer.self self = CM.transfer.self.cmd.create("", null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        CM.transfer.resources other = CM.transfer.resources.cmd.create("", "", null, "{nation_id}", null, null, null, null, null, null, null, null, null, null, null, null);
        CM.nation.stockpile stockpile = CM.nation.stockpile.cmd.create("nation:{nation_id}");

        CommandBehavior behavior = CommandBehavior.EPHEMERAL;

        IMessageBuilder msg = io.create().embed(title, body)
                .commandButton(behavior, channelId, deposits, "balance")
                .modal(behavior, channelId, self, "self")
                .modal(behavior, channelId, other, "other")
                .commandButton(behavior, channelId, stockpile, "stockpile");

        for (int i = 0; i < addButtons.size(); i++) {
            CommandRef add = addButtons.get(i);
            String label = addLabels.get(i);
            boolean isModal = isModals.get(i);
            if (isModal) {
                msg = msg.modal(behavior, channelId, add, label);
            } else {
                msg = msg.commandButton(behavior, channelId, add, label);
            }
        }
        msg.send();
    }


    @Command(desc="Generates sheets for a coalition war:" +
            "- All enemies\n" +
            "- Priority enemies\n" +
            "- All allies\n" +
            "- Underutilized allies")
    @RolePermission(Roles.ADMIN)
    public void allyEnemySheets(ValueStore store, NationPlaceholders placeholders,
            @Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel,
                                @Default SpreadSheet allEnemiesSheet,
                                @Default SpreadSheet priorityEnemiesSheet,
                                @Default SpreadSheet allAlliesSheet,
                                @Default SpreadSheet underutilizedAlliesSheet) throws GeneralSecurityException, IOException {
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        if (db.getCoalition(Coalition.ALLIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ALLIES.name() + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ENEMIES.name() + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
        if (allEnemiesSheet == null) {
            allEnemiesSheet = SpreadSheet.create(db, SheetKey.ENEMY_SHEET);
        }
        if (priorityEnemiesSheet == null) {
            priorityEnemiesSheet = SpreadSheet.create(db, SheetKey.PRIORITY_ENEMY_SHEET);
        }
        if (allAlliesSheet == null) {
            allAlliesSheet = SpreadSheet.create(db, SheetKey.ALLY_SHEET);
        }
        if (underutilizedAlliesSheet == null) {
            underutilizedAlliesSheet = SpreadSheet.create(db, SheetKey.UNDERUTILIZED_ALLY_SHEET);
        }

        Map.Entry<String, List<String>> allEnemies = Map.entry(
                "~enemies,#position>1,#vm_turns=0,#active_m<10800",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "'=MROUND({score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1)'",
                        "{color}",
                        "{beigeturns}",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'",
                        "{dayssincelastdefensivewarloss}",
                        "{dayssincelastoffensive}",
                        "{dayssince3consecutivelogins}",
                        "{dayssince4consecutivelogins}",
                        "{dayssince5consecutivelogins}"
                )
        );

        Map.Entry<String, List<String>> allAllies = Map.entry(
                "~allies,#position>1,#vm_turns=0,#active_m<10800",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "{beigeturns}",
                        "'=MROUND({score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1)'",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'",
                        "{dayssincelastdefensivewarloss}",
                        "{dayssincelastoffensive}",
                        "{dayssince3consecutivelogins}"
                )
        );

        Map.Entry<String, List<String>> priorityEnemies = Map.entry(
                "#cities>10,~enemies,#active_m<2880,#def<3,#off>0,#RelativeStrength>0.7,#vm_turns=0,#isbeige=0,#fighting(~allies)",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{strongestenemyrelative}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "'=MROUND({score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1)'",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'"
                )
        );

        Map.Entry<String, List<String>> underutilizedAllies = Map.entry(
                "~allies,#active_m<2880,#freeoffensiveslots>0,#tankpct>0.8,#aircraftpct>0.8,#RelativeStrength>1.3,#vm_turns=0,#isbeige=0",
                Arrays.asList(
                        "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{strongestenemyrelative}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "{beigeturns}",
                        "'=MROUND({score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1)'",
                        "{soldiers}",
                        "{tanks}",
                        "{aircraft}",
                        "{ships}",
                        "{missiles}",
                        "{nukes}",
                        "{spies}",
                        "={active_m}/60",
                        "{avg_daily_login}",
                        "'=\"{mmr}\"'"
                )
        );

        String footer = "";
        if (outputChannel != null) {
            footer = "\n\n> Output in " + outputChannel.getAsMention();
        }

        CommandBehavior behavior = CommandBehavior.UNPRESS;

        io.create()
                .embed("All Enemies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                        CM.nation.sheet.NationSheet.cmd.create(
                                allEnemies.getKey(),
                                StringMan.join(allEnemies.getValue(), " "),
                                null,
                                "sheet:" + allEnemiesSheet.getSpreadsheetId()
                        ), "update").send();

        io.create().embed("All Allies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.create(
                        allAllies.getKey(),
                        StringMan.join(allAllies.getValue(), " "),
                        null,
                        "sheet:" + allAlliesSheet.getSpreadsheetId()

                ), "update").send();
        io.create().embed("Priority Enemies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.create(
                        priorityEnemies.getKey(),
                        StringMan.join(priorityEnemies.getValue(), " "),
                        null,
                        "sheet:" + priorityEnemiesSheet.getSpreadsheetId()

                ), "update").send();

        io.create().embed("Underutilized Allies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.create(
                        underutilizedAllies.getKey(),
                        StringMan.join(underutilizedAllies.getValue(), " "),
                        null,
                        "sheet:" + underutilizedAlliesSheet.getSpreadsheetId()

                ), "update").send();
    }

    @Command(desc = "Discord embed for sheet to update ally and enemy spy counts, generate and send spy blitz targets")
    @RolePermission(Roles.ADMIN)
    public void spySheets(@Me GuildDB db, @Me IMessageIO io, @Default("spyops") @GuildCoalition String allies, @Default MessageChannel outputChannel, @Default SpreadSheet spySheet) throws GeneralSecurityException, IOException {
        if (allies == null) allies = Coalition.ALLIES.name();

        if (db.getCoalition(allies).isEmpty()) {
            throw new IllegalArgumentException("No `" + allies + "` coalition found (for enemy targets). See: " + CM.coalition.create.cmd.toSlashMention());
        }
        if (db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            throw new IllegalArgumentException("No `" + Coalition.ENEMIES.name() + "` coalition found. See: " + CM.coalition.create.cmd.toSlashMention());
        }
//        1. Update active ally spy counts
//        2. Update active enemy spy counts
//        3. Spy blitz sheet (spyops coalition)
//        4. Mail targets (spyops coalition)

        String title = "Spy Sheets";

        String footer = "\n\n(do not spam)";
        if (outputChannel != null) {
            footer += "\n\n> Output in " + outputChannel.getAsMention();
        }

        CommandBehavior behavior = CommandBehavior.UNPRESS;
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String columns = StringMan.join(Arrays.asList(
                "'=HYPERLINK(\"politicsandwar.com/nation/id={nation_id}\", \"{nation}\")'",
                "{alliancename}",
                "{relativestrength}",
                "{strongestenemyrelative}",
                "{cities}",
                "{score}",
                "{off}",
                "{def}",
                "'=MROUND({score}/" + PnwUtil.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                "'=MROUND({score}/0.4,1) & \"-\" & MROUND({score}/2.5,1)'",
                "{soldiers}",
                "{tanks}",
                "{aircraft}",
                "{ships}",
                "{missiles}",
                "{nukes}",
                "{spies}",
                "{war_policy}",
                "={active_m}/60",
                "{avg_daily_login}",
                "'=\"{mmr}\"'"
        ), " ");

        String spySheetId = spySheet != null ? spySheet.getSpreadsheetId() : SpreadSheet.create(db, SheetKey.SPYOP_SHEET).getSpreadsheetId();

        io.create().embed("Update ally", "Press `allies` to update active ally spy counts" + footer)
                .commandButton(behavior, channelId, CM.nation.sheet.NationSheet.cmd.create(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        columns,
                        "true",
                        null
                ), "allies").send();

        io.create().embed("Update enemy", "Press `enemies` to update active enemy spy counts" + footer)
                .commandButton(behavior, channelId, CM.nation.sheet.NationSheet.cmd.create(
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        columns,
                        "true",
                        null
                ), "enemies").send();

        io.create().embed("Blitz priority kills", "Press `blitz_kill` for a spy blitz sheet focusing spies/air" + footer)
        .commandButton(behavior, channelId, CM.spy.sheet.generate.cmd.create(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        StringMan.join(Arrays.asList(SpyCount.Operation.SPIES.name(),SpyCount.Operation.AIRCRAFT.name()), ","),
                        "true",
                        "true",
                        "true",
                        "sheet:" + spySheetId,
                        null,
                        null
                ), "blitz_kill").send();

        io.create().embed("Blitz priority damage", "Press `blitz_dmg` for a spy blitz sheet focusing damage" + footer)
                .commandButton(behavior, channelId, CM.spy.sheet.generate.cmd.create(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10",
                        null,
                        "true",
                        "true",
                        "true",
                        "sheet:" + spySheetId,
                        null,
                        null
                ), "blitz_dmg").send();
        io.create().embed("Validate and send", """
                        Press `check` to validate spy blitz sheet
                        Press `mail` to mail targets
                        """ + footer)
                .commandButton(behavior, channelId, CM.spy.sheet.validate.cmd.create(
                        "sheet:" + spySheetId,
                        null,
                        "~" + allies
                ), "validate")
        .commandButton(behavior, channelId, CM.mail.targets.cmd.create(
                        null,
                        "sheet:" + spySheetId,
                        "~" + allies,
                        null,
                        "true",
                        null,
                        null,
                        null,
                        null
                ), "mail").send();
    }

    @Command(desc = "Create an embed to view a google document for multiple nations, with random variations for each receiver")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public void announceDocument(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me IMessageIO io,
                                @Me User author,
                                GoogleDoc original,
                                NationList sendTo,
                                @Arg("Lines of replacement words or phrases, separated by `|` for each variation\n" +
                                        "Add multiple lines for each replacement you want") @TextArea String replacements) throws IOException, GeneralSecurityException {
        String title = original.getTitle();
        if (title == null) title = "Document";

        String announcement = original.readHtml();
        int annId = db.addAnnouncement(author, title, announcement, replacements, sendTo.getFilter(), true);

        List<String> replacementLines = Announcement.getReplacements(replacements);
        Collection<DBNation> nations = sendTo.getNations();
        Set<String> results = StringMan.enumerateReplacements(announcement, replacementLines, nations.size() + 1000, 0, 0);

        CM.announcement.view cmd = CM.announcement.view.cmd.create(annId + "", "true", null);

        StringBuilder body = new StringBuilder();
        body.append("Title: `" + title + "`\n");
        body.append("Can View: `" + sendTo.getFilter() + "` (" + nations.size() + " nations)\n");
        body.append("ID: `#" + annId + "`\n\n");
        body.append("Press `view` to view the document");
        io.create().embed(title, body.toString())
                .commandButton(CommandBehavior.EPHEMERAL, cmd, "view").send();
    }
}
