package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.MathOperation;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.announce.AnnounceType;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.sheet.GoogleDoc;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
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
    public String title(@Me User user, @Me IMessageIO io, @Me Guild guild, Message discMessage, String title) {
        checkMessagePerms(user, guild, discMessage);
        DiscordMessageBuilder message = new DiscordMessageBuilder(discMessage.getChannel(), discMessage);
        List<EmbedShrink> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        EmbedShrink embed = embeds.get(0);

        EmbedShrink builder = new EmbedShrink(embed);
        builder.setTitle(title);

        message.clearEmbeds();
        message.embed(builder);
        message.send();
        io.create().embed("Set Title", "Done! See: " + discMessage.getJumpUrl()).cancelButton("Dismiss").send();
        return null;
    }

    @Command(desc = "Set the description of an embed from this bot\n" +
            "Use backslash n for newline and `{description}` to include the existing description")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String description(@Me User user, @Me IMessageIO io, @Me Guild guild, Message discMessage, String description) {
        checkMessagePerms(user, guild, discMessage);
        DiscordMessageBuilder message = new DiscordMessageBuilder(discMessage.getChannel(), discMessage);
        List<EmbedShrink> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        EmbedShrink embed = embeds.get(0);

        EmbedShrink builder = new EmbedShrink(embed);
        description = description.replace("\\n", "\n");
        String existing = embed.getDescription().get();
        if (existing != null && description.contains("{description}")) {
            description = description.replace("{description}", existing);
        }
        builder.setDescription(description);

        message.clearEmbeds();
        message.embed(builder);
        message.send();
        io.create().embed("Set Description", "Done! See: " + discMessage.getJumpUrl()).cancelButton("Dismiss").send();
        return null;
    }

    @Command(desc = "Remove a button from an embed from this bot")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String renameButton(@Me User user, @Me IMessageIO io, @Me Guild guild, Message message, String label, String rename_to) {
        checkMessagePerms(user, guild, message);
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            throw new IllegalArgumentException("The message you linked is not from the bot. Only bot messages can be modified.");
        }
        if (!rename_to.matches("[a-zA-Z0-9_ ]+")) {
            throw new IllegalArgumentException("Label must be alphanumeric, not: `" + rename_to + "`");
        }

        Button found = null;
        List<Button> buttons = message.getButtons();
        for (Button button : buttons) {
            if (button.getLabel().equalsIgnoreCase(label)) {
                found = button;
                break;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("Button `" + label + "` not found on the embed");
        }
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) {
            throw new IllegalArgumentException("No embeds found on message: `" + message.getId() + "`");
        }
        Map<String, String> reactions = DiscordUtil.getReactions(embeds.get(0));

        DiscordMessageBuilder msg = new DiscordMessageBuilder(message.getChannel(), message);
        msg.clearButtons();

        if (reactions != null) {
            for (Map.Entry<String, String> entry : reactions.entrySet()) {
                msg.commandButton(entry.getValue(), entry.getKey().equalsIgnoreCase(label) ? rename_to : entry.getKey());
            }
        }
        for (Button button : message.getButtons()) {
            if (!button.getId().equalsIgnoreCase(button.getLabel())) {
                msg.commandButton(button.getId(), button.getLabel().equalsIgnoreCase(label) ? rename_to : button.getLabel());
            }
        }
        msg.send();
        io.create().embed("Renamed Button", "Done! Renamed button `" + label + "` to " + rename_to + "\n" +
                "Remove it using: " + CM.embed.remove.button.cmd.toSlashMention()).cancelButton("Dismiss").send();
        return null;
    }

    @Command(desc = "Remove a button from an embed from this bot")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String removeButton(@Me User user, @Me IMessageIO io, @Me Guild guild, Message message, @Arg("A comma separated list of button labels") @TextArea(',') List<String> labels) {
        checkMessagePerms(user, guild, message);
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            throw new IllegalArgumentException("The message you linked is not from the bot. Only bot messages can be modified.");
        }
        Set<String> labelSet = Set.copyOf(labels);

        Set<String> invalidLabels = new HashSet<>(labels);
        Set<String> validLabels = new ObjectLinkedOpenHashSet<>();
        List<ActionRow> rows = new ArrayList<>(message.getActionRows());
        for (int i = 0; i < rows.size(); i++) {
            ActionRow row = rows.get(i);
            List<ActionRowChildComponentUnion> components = new ArrayList<>(row.getComponents());
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
        io.create().embed("Deleted Button", "Done! Deleted " + labels.size() + " buttons").cancelButton("Dismiss").send();
        return null;
    }

    @Command(desc = """
            Add a button to a discord embed from this bot which runs a command
            Supports legacy commands and user command syntax.
            Unlike `embed add button`, this does not parse and validate command input.""")
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
        io.create().embed("Added Button", "Added button `" + label + "` to " + message.getJumpUrl() + "\n" +
                "Remove it using: " + CM.embed.remove.button.cmd.toSlashMention() + "\n" +
                "Rename using " + CM.embed.rename.button.cmd.toSlashMention()).cancelButton("Dismiss").send();// + CM.embed.rename.button.cmd.toSlashMention();
        return null;
    }

    @Command(desc = "Add a button to a discord embed from this bot which runs a command")
    @NoFormat
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String addButton(@Me User user, @Me IMessageIO io, @Me JSONObject cmdJson, @Me Guild guild, Message message, String label, CommandBehavior behavior, ICommand<?> command,
                            @Default @Arg("""
                                    The arguments and values you want to submit to the command
                                    Example: `myarg1:myvalue1 myarg2:myvalue2`
                                    For placeholders: <https://github.com/xdnw/locutus/wiki/nation_placeholders>""")
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
                        "See: " + CM.help.command.cmd.command(command.getFullPath()));
            }
        }

        String commandStr =  command.toCommandArgs(parsed);
        return addButtonRaw(user, io, cmdJson, guild, message, label, behavior, commandStr, channel, force);
    }

    @Command(desc = "Add a modal button to a discord embed from this bot, which creates a prompt for a command")
    @NoFormat
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String addModal(@Me User user, @Me IMessageIO io, @Me Guild guild, Message message, String label, CommandBehavior behavior, ICommand<?> command,
                           @Arg("A comma separated list of the command arguments to prompt for\n" +
                                   "Arguments can be one of the named arguments for the command, or the name of any `{placeholder}` you have for `defaults`") String arguments,
                           @Arg("""
                                   The default arguments and values you want to submit to the command
                                   Example: `myarg1:myvalue1 myarg2:myvalue2`
                                   For placeholders: <https://github.com/xdnw/locutus/wiki/nation_placeholders>""")
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
                        "See: " + CM.help.command.cmd.command(command.getFullPath()));
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
        io.create().embed("Added Modal", "Added modal button `" + label + "` to " + message.getJumpUrl()).cancelButton("Dismiss").send();
        return null;
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
    public void raid(@Me IMessageIO io, @Default MessageChannel outputChannel, @Default CommandBehavior behavior) {
        if (behavior == null) behavior = CommandBehavior.UNPRESS;
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

        CM.war.find.raid app = CM.war.find.raid.cmd.numResults("10");
        CM.war.find.raid members = CM.war.find.raid.cmd.targets(
                "*").numResults("25");
        CM.war.find.raid beige = CM.war.find.raid.cmd.targets(
                "*").numResults("25").beigeTurns("24");
        CM.war.find.raid ground = CM.war.find.raid.cmd.targets(
                "#tankpct<0.2,#soldierpct<0.4,*").numResults("25").activeTimeCutoff("0d").weakground("true");
        CM.war.find.raid ground_2d = CM.war.find.raid.cmd.targets(
                "#tankpct<0.2,#soldierpct<0.4,*").numResults("25").activeTimeCutoff("2d").weakground("true");
        CM.war.find.raid losing = CM.war.find.raid.cmd.targets(
                "#def>0,#RelativeStrength<1,*").numResults("25").activeTimeCutoff("0d").weakground("true");
        CM.war.find.unprotected unprotected = CM.war.find.unprotected.cmd.targets(
                "*").numResults("25").includeAllies("true").ignoreODP("true").maxRelativeCounterStrength("90");

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
        public void unblockadeRequests(@Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel, @Default CommandBehavior behavior) {
            if (behavior == null) behavior = CommandBehavior.UNPRESS;
            if (db.getCoalition(Coalition.ALLIES).isEmpty()) {
                throw new IllegalArgumentException("No `" + Coalition.ALLIES.name() + "` coalition. See " + CM.coalition.create.cmd.toSlashMention());
            }
            db.getOrThrow(GuildKey.UNBLOCKADE_REQUESTS);
            db.getOrThrow(GuildKey.UNBLOCKADED_ALERTS);
            if (Roles.UNBLOCKADED_ALERT.toRole2(db) == null) {
                throw new IllegalArgumentException(Roles.UNBLOCKADED_ALERT.toDiscordRoleNameElseInstructions(db.getGuild()));
            }

            Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
            String title = "Blockade Target & Requests";
            String body = """
                    **Request your blockade broken**
                    See e.g.: """ + CM.war.blockade.request.cmd.diff("3d").note("some reason").toSlashCommand(true)+ """
                    Press `Low` if low on resources
                    Press `deposit` if you need to deposit
                    Press `broke` if you are out of resources
                    ---
                    Press `break` to find enemies w/ blockades
                    Press `unpowered` to find enemies w/ blockades on unpowered allies
                    """;
            body += "\nSee e.g: " + CM.war.blockade.find.cmd.allies("~allies").myShips("250").toSlashCommand();

            if (channelId != null) {
                body += "\n\n> Results in <#" + channelId + ">";
            }

            CM.war.blockade.request low = CM.war.blockade.request.cmd.diff("3d").note("Low on resources");
            CM.war.blockade.request deposit = CM.war.blockade.request.cmd.diff("3d").note("Need to deposit");
            CM.war.blockade.request broke = CM.war.blockade.request.cmd.diff("3d").note("Broke");
            CM.war.blockade.find breakCmd = CM.war.blockade.find.cmd.allies("~allies,#active_m<2880").numResults("10");
            CM.war.blockade.find breakUnpowered = CM.war.blockade.find.cmd.allies("~allies,#ispowered=0,#active_m<2880").numResults("10");

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
        public void memberEconPanel(@Me IMessageIO io, @Default MessageChannel outputChannel, @Default CommandBehavior behavior, @Switch("d") boolean showDepositsInDms) {
            if (behavior == null) behavior = CommandBehavior.UNPRESS;
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

            CM.offshore.send send = CM.offshore.send.cmd.createEmpty();
            CM.deposits.check deposits = CM.deposits.check.cmd.nationOrAllianceOrGuild("{nation_id}").replyInDMs(showDepositsInDms ? "true" : null);
            CM.deposits.check depositsBreakdown = CM.deposits.check.cmd.nationOrAllianceOrGuild("{nation_id}").showCategories("true");
            CM.tax.info taxInfo = CM.tax.info.cmd.nation("{nation_id}");
            CM.nation.revenue revenue = CM.nation.revenue.cmd.nations("{nation_id}").includeUntaxable("true");
            CM.city.optimalBuild optimalbuild = CM.city.optimalBuild.cmd.build("{city 1}");
            CM.trade.price tradeprice = CM.trade.price.cmd.createEmpty();
            CM.trade.margin trademargin = CM.trade.margin.cmd.createEmpty();
            CM.trade.profit tradeprofit = CM.trade.profit.cmd.nations("{nation_id}").time("7d");

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
    public void warGuerilla(@Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel, @Default CommandBehavior behavior) {
        if (behavior == null) behavior = CommandBehavior.UNPRESS;
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

        CM.war.find.damage damage = CM.war.find.damage.cmd.nations(
                "~enemies");
        CM.war.find.damage damageWeak = CM.war.find.damage.cmd.nations(
                "~enemies").includeApps("true").includeInactives("true").filterWeak("true");
        CM.war.find.damage damageNoNavy = CM.war.find.damage.cmd.nations(
                "~enemies").noNavy("true");
        CM.war.find.damage damageNoVDS = CM.war.find.damage.cmd.nations(
                "~enemies,#hasProject(vital_defense_system)=0");
        CM.war.find.damage damageNoID = CM.war.find.damage.cmd.nations(
                "~enemies,#hasProject(iron_dome)=0");
        CM.war.find.damage damageNoVDSID = CM.war.find.damage.cmd.nations(
                "~enemies,#hasProject(iron_dome)=0,#hasProject(vital_defense_system)=0");

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
    public void warContestedRange(@Me GuildDB db, @Me IMessageIO io,
                                  @Arg("If the cutoff is greater or less than the score") MathOperation greaterOrLess,
                                  @Arg("The score at which the conflict is not contestable")
                                  double score, @Default MessageChannel outputChannel, @Default CommandBehavior behavior, @Switch("d") boolean resultsInDm) {
        if (behavior == null) behavior = CommandBehavior.UNPRESS;
        if (greaterOrLess == MathOperation.EQUAL || greaterOrLess == MathOperation.NOT_EQUAL) {
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

        MathOperation opposite = greaterOrLess.opposite();
        boolean greater = greaterOrLess == MathOperation.GREATER || greaterOrLess == MathOperation.GREATER_EQUAL;
        double minScore = greater ? score : 0;
        double maxScore = greater ? Integer.MAX_VALUE : score;
        String rangeStr = String.format("%.2f", minScore) + "," + String.format("%.2f", maxScore);

        String dmStr = resultsInDm ? "true" : null;
        CM.war.find.enemy easy = CM.war.find.enemy.cmd.targets(
                "~enemies,#off>0").onlyEasy("true").resultsInDm(dmStr);
        int scoreMax;
        if (greaterOrLess == MathOperation.GREATER || greaterOrLess == MathOperation.GREATER_EQUAL) {
            scoreMax = (int) Math.ceil(score / 0.75);
        } else {
            scoreMax = (int) Math.ceil(score * 0.75);
        }
        int scoreInt = (int) score;
        CM.war.find.enemy high = CM.war.find.enemy.cmd.targets(
                "~enemies,#off>0,#score" + opposite + scoreMax + ",#strongestEnemyOfScore" + rangeStr + "<1,#strongestEnemyOfScore" + rangeStr + ">0.66").onlyPriority("true").resultsInDm(dmStr);
        CM.war.find.enemy low = CM.war.find.enemy.cmd.targets(
                "~enemies,#off>0,#score" + opposite + scoreMax + ",#strongestEnemyOfScore" + rangeStr + "<1,#strongestEnemyOfScore" + rangeStr + ">0.66").onlyPriority("true").onlyWeak("true").resultsInDm(dmStr);
        CM.war.find.enemy weak = CM.war.find.enemy.cmd.targets(
                "~enemies").includeInactives("true").includeApplicants("true").onlyEasy("true").resultsInDm(dmStr).includeStrong("true");
        CM.war.find.damage infra = CM.war.find.damage.cmd.nations(
                "~enemies,#active_m>2880|~enemies,#score" + greaterOrLess + scoreMax +"|~enemies,#barracks=0,#off=0").includeApps("true").includeInactives("true").resultsInDm(dmStr);

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
  public void spyEnemy(@Me GuildDB db, @Me IMessageIO io, @Default @GuildCoalition String coalition, @Default MessageChannel outputChannel, @Default CommandBehavior behavior) {
      if (behavior == null) behavior = CommandBehavior.UNPRESS;
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

      CM.spy.find.target spy = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<1440").operations(Operation.SPIES.name());
        CM.spy.find.target airplane = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<1440").operations(Operation.AIRCRAFT.name());
        CM.spy.find.target tank = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<1440").operations(Operation.TANKS.name());
        CM.spy.find.target ship = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<1440").operations(Operation.SHIPS.name());
        CM.spy.find.target missile = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<2880").operations(Operation.MISSILE.name());
        CM.spy.find.target nuke = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<2880").operations(Operation.NUKE.name());
        CM.spy.find.target dmg = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<1440").operations("*");
        CM.spy.find.target kill = CM.spy.find.target.cmd.targets(
                "~" + coalition + ",#active_m<1440").operations("*").prioritizeKills("true");

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
    @Command(desc= """
            Enemy war targets when you are winning
            Prioritizes down declares
            To find contestable range, see: strengthTierGraph""")
    @RolePermission(Roles.ADMIN)
    public void warWinning(@Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel, @Default CommandBehavior behavior, @Switch("d") boolean resultsInDm) {
        if (behavior == null) behavior = CommandBehavior.UNPRESS;
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
        CM.war.find.enemy high = CM.war.find.enemy.cmd.targets(
                "~enemies,#fighting(~allies),#getStrongestEnemy()>0.66").onlyPriority("true").onlyEasy("true")
                .resultsInDm(dmStr);
        CM.war.find.enemy med = CM.war.find.enemy.cmd.targets(
                "~enemies,#fighting(~allies)").onlyPriority("true").onlyEasy("true").resultsInDm(dmStr);
        CM.war.find.enemy low = CM.war.find.enemy.cmd.targets(
                "~enemies").onlyPriority("true").onlyEasy("true").resultsInDm(dmStr);
        CM.war.find.enemy easy = CM.war.find.enemy.cmd.targets(
                "~enemies").onlyEasy("true").resultsInDm(dmStr);
        CM.war.find.enemy inactive = CM.war.find.enemy.cmd.targets(
                "~enemies").includeInactives("true").includeApplicants("true").onlyEasy("true").resultsInDm(dmStr);
        CM.war.find.damage infra = CM.war.find.damage.cmd.nations(
                "~enemies").includeApps("true").includeInactives("true").resultsInDm(dmStr);
        CM.war.find.enemy beige = CM.war.find.enemy.cmd.targets(
                "~enemies,#color=beige").includeInactives("true").includeApplicants("true").onlyEasy("true").resultsInDm(dmStr);

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
    public void econPanel(@Me GuildDB db, @Me IMessageIO io, @Switch("c") MessageChannel outputChannel, @Switch("b") CommandBehavior behavior, @Switch("n") DepositType useFlowNote, @Arg("Include past depositors in deposits sheet") @Switch("p") Set<Integer> includePastDepositors) {
        if (behavior == null) behavior = outputChannel == null ? CommandBehavior.EPHEMERAL : CommandBehavior.UNPRESS;
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



        IMessageBuilder msg = io.create().embed(title, body);
        if (offshoreBalance) {
            msg = msg.commandButton(behavior, channelId, CM.offshore.accountSheet.cmd.createEmpty(), "offshore");
        }
        if (offshoreSend) {
            msg = msg.commandButton(behavior, channelId, CM.offshore.send.cmd.createEmpty(), "offshore");
        }
        // deposits
        msg = msg.commandButton(behavior, channelId, CM.deposits.sheet.cmd.includePastDepositors(pastDepositorsStr).useFlowNote(useFlowNoteStr), "deposits");
        // stockpile
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.stockpileSheet.cmd.createEmpty(), "stockpile");
        // revenue
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.revenueSheet.cmd.nations(allianceStr), "revenue");
        // bracket
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.taxBracketSheet.cmd.createEmpty(), "bracket");
        // tax
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.taxRevenue.cmd.createEmpty(), "tax");
        // warchest
        msg = msg.commandButton(behavior, channelId, CM.sheets_econ.warchestSheet.cmd.nations(allianceStr), "warchest");
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
    public void iaPanel(@Me GuildDB db, @Me IMessageIO io, @Switch("c") MessageChannel outputChannel, @Switch("b") CommandBehavior behavior) {
        if (behavior == null) behavior = outputChannel == null ? CommandBehavior.EPHEMERAL : CommandBehavior.UNPRESS;
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

        String allianceStr = db.getAllianceIds().stream().map(f -> "AA:" + f).collect(Collectors.joining(",")) + ",#position>1,#vm_turns=0";

        IMessageBuilder msg = io.create().embed(title, body);
        // audit
        msg = msg.commandButton(behavior, channelId, CM.audit.sheet.cmd.createEmpty(), "audit");
        // mail
        msg = msg.commandButton(behavior, channelId, CM.audit.run.cmd.nationList(allianceStr).mailResults("true"), "mail");
        // activity
        msg = msg.commandButton(behavior, channelId, CM.sheets_ia.ActivitySheet.cmd.nations(allianceStr), "activity");
        // daychange
        msg = msg.commandButton(behavior, channelId, CM.sheets_ia.daychange.cmd.nations(allianceStr), "dc");
        // spies
        msg = msg.commandButton(behavior, channelId, CM.audit.hasNotBoughtSpies.cmd.nations(allianceStr), "spies");
        // mmr
        msg = msg.commandButton(behavior, channelId, CM.sheets_milcom.MMRSheet.cmd.nations(allianceStr), "mmr");
        // auto
        msg = msg.commandButton(behavior, channelId, CM.role.autoassign.cmd.createEmpty(), "auto");

        msg.send();
    }

    @Command(desc = "Discord embed for checking deposits, withdrawing funds, viewing your stockpile, depositing resources and offshoring funds")
    @HasOffshore
    @RolePermission(Roles.ADMIN)
    public void depositsPanel(@Me GuildDB db, @Me IMessageIO io,
                              @Arg("Only applicable to corporate servers. The nation accepting trades for bank deposits. Defaults to the bot owner's nation")
                              @Default DBNation bankerNation,
                              @Switch("c") MessageChannel outputChannel,
                              @Default CommandBehavior behavior) {
        if (behavior == null) behavior = CommandBehavior.EPHEMERAL;
        int nationId = Locutus.loader().getNationId();
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
            body += "\nTo deposit, send a PRIVATE trade offer to " + PW.getMarkdownUrl(nationId, false) + ":\n" +
                    "- Selling a resource for $0\n" +
                    "- Buying food for OVER $100,000\n" +
                    "Press `trade deposit` if you have sent trades\n" +
                    "Press `trade deposit amount` if you want to create trades for an amount\n";

            addButtons.add(CM.trade.accept.cmd.receiver(nationId + ""));
            addLabels.add("deposit trade");
            isModals.add(false);

            addButtons.add(CM.trade.accept.cmd.receiver(nationId + "").amount(""));
            addLabels.add("deposit trade amount");
            isModals.add(false);
        } else {
            body += "\nTo deposit, go to your alliance bank page in-game.\n" +
                    "Alternatively, set your api key with: " + CM.credentials.addApiKey.cmd.toSlashMention() + "\n" +
                    "And then press" +
                    "- `deposit custom` or `deposit auto`" +
                    "- `offshore` to offshore funds";
            addButtons.add(CM.bank.deposit.cmd.nations("nation:{nation_id}").amount("").useApi("true").force("true"));
            addLabels.add("deposit custom");
            isModals.add(true);


            CommandRef depositAuto = CM.bank.deposit.cmd.nations("nation:{nation_id}").rawsDays("7").keepWarchestFactor("1").useApi("true").force("true");
            addButtons.add(depositAuto);
            addLabels.add("deposit auto");
            isModals.add(false);
            CommandRef offshore = CM.offshore.send.cmd.createEmpty();
            addButtons.add(offshore);
            addLabels.add("offshore");
            isModals.add(false);
        }

        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CM.deposits.check deposits = CM.deposits.check.cmd.nationOrAllianceOrGuild("nation:{nation_id}");
        CM.transfer.self self = CM.transfer.self.cmd.amount("");
        CM.transfer.resources other = CM.transfer.resources.cmd.receiver("").transfer("").nation_account("{nation_id}").bank_note("#ignore");
        CM.nation.stockpile stockpile = CM.nation.stockpile.cmd.nationOrAlliance("nation:{nation_id}");

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


    @Command(desc= """
            Generates sheets for a coalition war:\
            - All enemies
            - Priority enemies
            - All allies
            - Underutilized allies""")
    @RolePermission(Roles.ADMIN)
    public void allyEnemySheets(@Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel,
                                @Default SpreadSheet allEnemiesSheet,
                                @Default SpreadSheet priorityEnemiesSheet,
                                @Default SpreadSheet allAlliesSheet,
                                @Default SpreadSheet underutilizedAlliesSheet, @Default CommandBehavior behavior) throws GeneralSecurityException, IOException {
        if (behavior == null) behavior = CommandBehavior.UNPRESS;
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

        Map.Entry<String, List<String>> allEnemies = KeyValue.of(
                "~enemies,#position>1,#vm_turns=0,#active_m<10800",
                Arrays.asList(
                        "'=HYPERLINK(\"" + Settings.PNW_URL() + "/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "'=MROUND({score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1)'",
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

        Map.Entry<String, List<String>> allAllies = KeyValue.of(
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
                        "'=MROUND({score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1)'",
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

        Map.Entry<String, List<String>> priorityEnemies = KeyValue.of(
                "#cities>10,~enemies,#active_m<2880,#def<3,#off>0,#RelativeStrength>0.7,#vm_turns=0,#isbeige=0,#fighting(~allies)",
                Arrays.asList(
                        "'=HYPERLINK(\"" + Settings.PNW_URL() + "/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{strongestenemyrelative}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "'=MROUND({score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1)'",
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

        Map.Entry<String, List<String>> underutilizedAllies = KeyValue.of(
                "~allies,#active_m<2880,#freeoffensiveslots>0,#tankpct>0.8,#aircraftpct>0.8,#RelativeStrength>1.3,#vm_turns=0,#isbeige=0",
                Arrays.asList(
                        "'=HYPERLINK(\"" + Settings.PNW_URL() + "/nation/id={nation_id}\", \"{nation}\")'",
                        "{alliancename}",
                        "{relativestrength}",
                        "{strongestenemyrelative}",
                        "{cities}",
                        "{score}",
                        "{off}",
                        "{def}",
                        "{beigeturns}",
                        "'=MROUND({score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                        "'=MROUND({score}*0.75,1) & \"-\" & MROUND( {score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1)'",
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

        io.create()
                .embed("All Enemies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                        CM.nation.sheet.NationSheet.cmd.nations(
                                allEnemies.getKey()).columns(
                                StringMan.join(allEnemies.getValue(), " ")).sheet(
                                "sheet:" + allEnemiesSheet.getSpreadsheetId()
                        ), "update").send();

        io.create().embed("All Allies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.nations(
                        allAllies.getKey()).columns(
                        StringMan.join(allAllies.getValue(), " ")).sheet(
                        "sheet:" + allAlliesSheet.getSpreadsheetId()

                ), "update").send();
        io.create().embed("Priority Enemies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.nations(
                        priorityEnemies.getKey()).columns(
                        StringMan.join(priorityEnemies.getValue(), " ")).sheet(
                        "sheet:" + priorityEnemiesSheet.getSpreadsheetId()

                ), "update").send();

        io.create().embed("Underutilized Allies Sheet", "Press `update` to update" + footer).commandButton(behavior, channelId,
                CM.nation.sheet.NationSheet.cmd.nations(
                        underutilizedAllies.getKey()).columns(
                        StringMan.join(underutilizedAllies.getValue(), " ")).sheet(
                        "sheet:" + underutilizedAlliesSheet.getSpreadsheetId()

                ), "update").send();
    }

    @Command(desc = "Discord embed for sheet to update ally and enemy spy counts, generate and send spy blitz targets")
    @RolePermission(Roles.ADMIN)
    public void spySheets(@Me GuildDB db, @Me IMessageIO io,
                          @Default("spyops") @GuildCoalition String allies,
                          @Default MessageChannel outputChannel,
                          @Default SpreadSheet spySheet,
                          @Default CommandBehavior behavior) throws GeneralSecurityException, IOException {
        if (behavior == null) behavior = CommandBehavior.UNPRESS;
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

        String footer = "\n\n(do not spam)";
        if (outputChannel != null) {
            footer += "\n\n> Output in " + outputChannel.getAsMention();
        }

        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();

        String columns = StringMan.join(Arrays.asList(
                "'=HYPERLINK(\"" + Settings.PNW_URL() + "/nation/id={nation_id}\", \"{nation}\")'",
                "{alliancename}",
                "{relativestrength}",
                "{strongestenemyrelative}",
                "{cities}",
                "{score}",
                "{off}",
                "{def}",
                "'=MROUND({score}/" + PW.WAR_RANGE_MAX_MODIFIER + ",1) & \"-\" & MROUND({score}/0.75,1)'",
                "'=MROUND({score}/0.4,1) & \"-\" & MROUND({score}/2.5,1)'",
                "{soldiers}",
                "{tanks}",
                "{aircraft}",
                "{ships}",
                "{missiles}",
                "{nukes}",
                "{spies}",
                "{warpolicy}",
                "={active_m}/60",
                "{avg_daily_login}",
                "'=\"{mmr}\"'",
                "{hasProject(" + Projects.INTELLIGENCE_AGENCY.name() + ")}",
                "{hasProject(" + Projects.SPY_SATELLITE.name() + ")}",
                "{hasProject(" + Projects.SURVEILLANCE_NETWORK.name() + ")}"
        ), " ");

        String spySheetId = spySheet != null ? spySheet.getSpreadsheetId() : SpreadSheet.create(db, SheetKey.SPYOP_SHEET).getSpreadsheetId();

        io.create().embed("Update ally", "Press `allies` to update active ally spy counts" + footer)
                .commandButton(behavior, channelId, CM.nation.sheet.NationSheet.cmd.nations(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10").columns(
                        columns
                ), "allies").send();

        io.create().embed("Update enemy", "Press `enemies` to update active enemy spy counts" + footer)
                .commandButton(behavior, channelId, CM.nation.sheet.NationSheet.cmd.nations(
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10").columns(
                        columns
                ), "enemies").send();

        io.create().embed("Blitz priority kills", "Press `blitz_kill` for a spy blitz sheet focusing spies/air" + footer)
        .commandButton(behavior, channelId, CM.spy.sheet.generate.cmd.attackers(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10").defenders(
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10").allowedTypes(
                        StringMan.join(Arrays.asList(Operation.SPIES.name(), Operation.AIRCRAFT.name()), ",")).forceUpdate(
                        "true").checkEspionageSlots(
                        "true").prioritizeKills(
                        "true").sheet(
                        "sheet:" + spySheetId
                ), "blitz_kill").send();

        io.create().embed("Blitz priority damage", "Press `blitz_dmg` for a spy blitz sheet focusing damage" + footer)
                .commandButton(behavior, channelId, CM.spy.sheet.generate.cmd.attackers(
                        "~" + allies + ",#vm_turns=0,#position>1,#active_m<1440,#cities>=10").defenders(
                        "~enemies,#vm_turns=0,#position>1,#active_m<1440,#cities>=10").forceUpdate(
                        "true").checkEspionageSlots(
                        "true").prioritizeKills(
                        "true").sheet(
                        "sheet:" + spySheetId
                ), "blitz_dmg").send();
        io.create().embed("Validate and send", """
                        Press `check` to validate spy blitz sheet
                        Press `mail` to mail targets
                        """ + footer)
                .commandButton(behavior, channelId, CM.spy.sheet.validate.cmd.sheet(
                        "sheet:" + spySheetId).filter(
                        "~" + allies
                ), "validate")
        .commandButton(behavior, channelId, CM.mail.targets.cmd.spySheet(
                        "sheet:" + spySheetId).allowedNations(
                        "~" + allies).sendFromGuildAccount(
                        "true").force("true"
                ), "mail").send();
    }

    @Command(desc = "Create an embed to view a google document for multiple nations, with random variations for each receiver")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public void announceDocument(@Me GuildDB db, @Me IMessageIO io,
                                @Me User author,
                                GoogleDoc original,
                                NationList sendTo,
                                @Arg("Lines of replacement words or phrases, separated by `|` for each variation\n" +
                                        "Add multiple lines for each replacement you want") @TextArea String replacements) throws IOException, GeneralSecurityException {
        String title = original.getTitle();
        if (title == null) title = "Document";

        String announcement = original.readHtml();
        int annId = db.addAnnouncement(AnnounceType.DOCUMENT, author, title, announcement, replacements, sendTo.getFilter(), true);

        List<String> replacementLines = Announcement.getReplacements(replacements);
        Collection<DBNation> nations = sendTo.getNations();
        Set<String> results = StringMan.enumerateReplacements(announcement, replacementLines, nations.size() + 1000, 0, 0);

        CM.announcement.view cmd = CM.announcement.view.cmd.ann_id(annId + "");

        StringBuilder body = new StringBuilder();
        body.append("Title: `" + title + "`\n");
        body.append("Can View: `" + sendTo.getFilter() + "` (" + nations.size() + " nations)\n");
        body.append("ID: `#" + annId + "`\n\n");
        body.append("Press `view` to view the document");
        io.create().embed(title, body.toString())
                .commandButton(CommandBehavior.EPHEMERAL, cmd, "view").send();
    }
}
