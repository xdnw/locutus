package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.FlowType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.test.IAChannel;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class DiscordCommands {
    @Command(desc = "Modify the permissions for a list of nations in a channel.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public static String channelPermissions(@Me Member author, @Me Guild guild, TextChannel channel, Set<DBNation> nations, Permission permission,
                                            @Arg("Negate the permission") @Switch("n") boolean negate,
                                            @Arg("Remove the permission from all other users")
                                            @Switch("r") boolean removeOthers,
                                            @Arg("Log the changes to user permissions that are made")
                                            @Switch("l") boolean listChanges,
                                            @Switch("p") boolean pingAddedUsers) throws ExecutionException, InterruptedException {
        if (!author.hasPermission(channel, Permission.MANAGE_PERMISSIONS))
            throw new IllegalArgumentException("You do not have " + Permission.MANAGE_PERMISSIONS + " in " + channel.getAsMention());

        Set<Member> members = new HashSet<>();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) {
                    members.add(member);
                }
            }
        }

        List<String> changes = new ArrayList<>();

        Set<Member> toRemove = new HashSet<>();

        for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
            Member member = override.getMember();
            if (member == null || member.getUser().isBot()) continue;

            boolean allowed = (override.getAllowedRaw() & permission.getRawValue()) > 0;
            boolean denied = (override.getDeniedRaw() & permission.getRawValue()) > 0;
            boolean contains = members.contains(member);
            boolean isSet = negate ? denied : allowed;
            if (contains && isSet) {
                members.remove(member);
            } else if (!contains && isSet && removeOthers) {
                toRemove.add(member);
            }
        }
        Function<Member, String> nameFuc = Member::getEffectiveName;
        if (pingAddedUsers) {
            listChanges = true;
            nameFuc = IMentionable::getAsMention;
        }

        List<Future<?>> tasks = new ArrayList<>();
        for (Member member : members) {
            PermissionOverrideAction override = channel.upsertPermissionOverride(member);
            PermissionOverrideAction action;
            if (negate) {
                action = override.deny(permission);
            } else {
                action = override.grant(permission);
            }
            tasks.add(RateLimitUtil.queue(action));

            changes.add("Set " + permission + "=" + !negate + " for " + nameFuc.apply(member));
        }

        for (Member member : toRemove) {
            tasks.add(RateLimitUtil.queue(channel.upsertPermissionOverride(member).clear(permission)));
            changes.add("Clear " + permission + " for " + nameFuc.apply(member));
        }

        for (Future<?> task : tasks) {
            task.get();
        }

        StringBuilder response = new StringBuilder("Done.");
        if (listChanges && !changes.isEmpty()) {
            response.append("\n- ").append(StringMan.join(changes, "\n- "));
        }
        return response.toString();
    }

    @Command(desc = "Have the bot say the provided message, with placeholders replaced.")
    @NoFormat
    public String say(NationPlaceholders placeholders, ValueStore store, @Me User author, @Me DBNation me, @TextArea String msg) {
        msg = DiscordUtil.trimContent(msg);
        msg = msg.replace("@", "@\u200B");
        msg = msg.replace("&", "&\u200B");

        GPTUtil.checkThrowModeration(msg);

        msg = msg + "\n\n- " + author.getAsMention();

        msg = placeholders.format2(store, msg, me, false);
        return msg;
    }

    @Command(desc = "Import all emojis from another guild", aliases = {"importEmoji", "importEmojis"})
    @RolePermission(Roles.ADMIN)
    public String importEmojis(@Me IMessageIO channel, Guild guild) throws ExecutionException, InterruptedException {
        if (!Settings.INSTANCE.DISCORD.CACHE.EMOTE) {
            throw new IllegalStateException("Please enable DISCORD.CACHE.EMOTE in " + Settings.INSTANCE.getDefaultFile());
        }
        if (!Settings.INSTANCE.DISCORD.INTENTS.EMOJI) {
            throw new IllegalStateException("Please enable DISCORD.INTENTS.EMOJI in " + Settings.INSTANCE.getDefaultFile());
        }
        List<RichCustomEmoji> emotes = guild.getEmojis();

        List<Future<?>> tasks = new ArrayList<>();
        for (RichCustomEmoji emote : emotes) {
            if (emote.isManaged() || !emote.isAvailable()) {
                continue;
            }

            String url = emote.getImageUrl();
            byte[] bytes = FileUtil.readBytesFromUrl(PagePriority.DISCORD_EMOJI_URL, url);

            channel.send("Creating emote: " + emote.getName() + " | " + url);

            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                tasks.add(RateLimitUtil.queue(guild.createEmoji(emote.getName(), icon)));
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        return "Done!";
    }

    @Command(desc = """
            Generate a card which runs a command when users react to it.
            Put commands inside "quotes".
            Prefix a command with a #channel e.g. `"#channel {prefix}embedcommand"` to have the command output go there

            Prefix the command with:`~{prefix}command` to remove the user's reaction upon use and keep the card
            `_{prefix}command` to remove ALL reactions upon use and keep the card
            `.{prefix}command` to keep the card upon use

            Example:
            `{prefix}embed 'Some Title' 'My First Embed' '~{prefix}fun say Hello {nation}' '{prefix}fun say "Goodbye {nation}"'`""",
            aliases = {"card", "embed"})
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    @NoFormat
    public String card(@Me IMessageIO channel, String title, String body, @TextArea List<String> commands) {
        try {
            String emoji = "\ufe0f\u20e3";

            if (commands.size() > 10) {
                return "Too many commands (max: 10, provided: " + commands.size() + ")\n" +
                        "Note: Commands must be inside \"double quotes\", and each subsequent command separated by a space.";
            }
            body = body.replace("\\n", "\n");

            IMessageBuilder msg = channel.create().embed(title, body);
            for (int i = 0; i < commands.size(); i++) {
                String cmd = commands.get(i);
                String codePoint = i + emoji;

                msg = msg.commandButton(cmd, codePoint);
            }
            msg.send();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    @Command(desc = "Create a channel with name in a specified category and ping the specified roles upon creation.")
    @NoFormat
    public String channel(NationPlaceholders placeholders, ValueStore store, @Me GuildDB db, @Me User author, @Me Guild guild, @Me IMessageIO output, @Me DBNation nation,
                          String channelName, Category category, @Default String copypasta,
                          @Switch("i") boolean addInternalAffairsRole,
                          @Switch("m") boolean addMilcom,
                          @Switch("f") boolean addForeignAffairs,
                          @Switch("e") boolean addEcon,
                          @Switch("p") boolean pingRoles,
                          @Switch("a") boolean pingAuthor

    ) throws ExecutionException, InterruptedException {
        if (category.getGuild().getIdLong() != db.getGuild().getIdLong()) {
            throw new IllegalArgumentException("Category is not in the same guild as the command.");
        }
        channelName = placeholders.format2(store, channelName, nation, true);

        Member member = guild.getMember(author);

        List<IPermissionHolder> holders = new ArrayList<>();
        holders.add(member);
        assert member != null;
        holders.addAll(member.getRoles());
        holders.add(guild.getRolesByName("@everyone", false).get(0));

        IMessageBuilder msg = output.getMessage();
        boolean hasOverride = msg != null && msg.getAuthor().getIdLong() == Settings.INSTANCE.APPLICATION_ID;
        for (IPermissionHolder holder : holders) {
            PermissionOverride overrides = category.getPermissionOverride(holder);
            if (overrides == null) continue;
            if (overrides.getAllowed().contains(Permission.MANAGE_CHANNEL)) {
                hasOverride = true;
                break;
            }
        }

        if (!hasOverride) {
            return "No permission to create channel in: " + category.getName();
        }

        Set<Roles> roles = new HashSet<>();
        if (addInternalAffairsRole) roles.add(Roles.INTERNAL_AFFAIRS);
        if (addMilcom) roles.add(Roles.MILCOM);
        if (addForeignAffairs) roles.add(Roles.FOREIGN_AFFAIRS);
        if (addEcon) roles.add(Roles.ECON);
        if (roles.isEmpty()) roles.add(Roles.INTERNAL_AFFAIRS);

        GuildMessageChannel createdChannel = null;
        List<TextChannel> channels = category.getTextChannels();
        for (TextChannel channel : channels) {
            if (channel.getName().equalsIgnoreCase(channelName)) {
                createdChannel = updateChannel(channel, member, roles);
                break;
            }
        }
        if (createdChannel == null) {
            createdChannel = updateChannel(RateLimitUtil.complete(category.createTextChannel(channelName)), member, roles);
            DiscordChannelIO io = new DiscordChannelIO(createdChannel);
            IMessageBuilder toSend = null;
            if (copypasta != null && !copypasta.isEmpty()) {
                String copyPasta = db.getCopyPasta(copypasta, true);
                if (copyPasta != null) {
                    if (toSend == null) toSend = io.create();
                    toSend.append(copyPasta);
                }
            }
            if (pingRoles) {
                for (Roles dept : roles) {
                    Role role = dept.toRole2(guild);
                    if (role != null) {
                        if (toSend == null) toSend = io.create();
                        toSend.append("\n" + role.getAsMention());
                    }
                }
            }
            if (pingAuthor) {
                if (toSend == null) toSend = io.create();
                toSend.append("\n" + author.getAsMention());
            }
            if (toSend != null) toSend.send();
        }

        return "Channel: " + createdChannel.getAsMention();
    }

    private TextChannel updateChannel(TextChannel channel, IPermissionHolder holder, Set<Roles> depts) {
        RateLimitUtil.complete(channel.upsertPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL));
        RateLimitUtil.complete(channel.upsertPermissionOverride(holder).grant(Permission.VIEW_CHANNEL));

        for (Roles dept : depts) {
            Role role = dept.toRole2(channel.getGuild());
            if (role != null) {
                RateLimitUtil.complete(channel.upsertPermissionOverride(role).grant(Permission.VIEW_CHANNEL));
            }
        }
        return channel;
    }

    @Command(desc = "Show the title, description and commands for a bot embed", viewable = true)
    public String embedInfo(Message embedMessage, @Arg("Show commands to update`copyToMessage` with the info from the `embedMessage`") @Default Message copyToMessage) {
        List<MessageEmbed> embeds = embedMessage.getEmbeds();
        if (embeds.size() != 1) return "No embed found.";

        MessageEmbed embed = embeds.get(0);
        String title = embed.getTitle();
        String desc = embed.getDescription();


        Map<String, List<DiscordUtil.CommandInfo>> commandMap = DiscordUtil.getCommands(embedMessage.isFromGuild() ? embedMessage.getGuild() : null, embed, embedMessage.getButtons(), embedMessage.getJumpUrl(), true);
        List<String> commands = new ArrayList<>();

        commands.add(CM.embed.create.cmd.title(title).description(desc).toSlashCommand(false));

        String url = copyToMessage == null ? "" : copyToMessage.getJumpUrl();

        for (Map.Entry<String, List<DiscordUtil.CommandInfo>> entry : commandMap.entrySet()) {
            CommandBehavior behavior = null;
            Long channelId = null;
            List<String> current = new ArrayList<>();
            for (DiscordUtil.CommandInfo info : entry.getValue()) {
                if (info.behavior != null) {
                    behavior = info.behavior;
                }
                if (info.channelId != null) {
                    channelId = info.channelId;
                }
                current.add(info.command);
            }
            String label = entry.getKey();

            String behaviorStr = (behavior == null ? CommandBehavior.DELETE_MESSAGE : behavior).name();
            String cmdStr = CM.embed.add.raw.cmd.message(url).label(label).behavior(behaviorStr).command(StringMan.join(current, "\n")).channel(channelId == null ? null : channelId.toString()).toSlashCommand(false);
            commands.add(cmdStr);
        }

        return "Run the following commands:\n" +
                "```\n" +
                StringMan.join(commands, "\n").replace("```", "\\`\\`\\`") +
                "\n```";
    }

    @Command(desc = "Update a bot embed")
    public String updateEmbed(@Me Guild guild, @Me User user, @Me IMessageIO io, @Switch("r") @RegisteredRole Roles requiredRole, @Switch("c") Color color, @Switch("t") String title, @Switch("d") String desc) {
        IMessageBuilder message = io.getMessage();

        if (message == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID)
            return "This command can only be run when bound to a Locutus embed.";
        if (requiredRole != null) {
            if (!requiredRole.has(user, guild)) {
                return null;
            }
        }
        if (!io.isInteraction() && !Roles.INTERNAL_AFFAIRS.has(user, guild)) {
            return "Missing: " + Roles.INTERNAL_AFFAIRS.toDiscordRoleNameElseInstructions(guild);
        }

        List<EmbedShrink> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        EmbedShrink embed = embeds.get(0);

        EmbedShrink builder = new EmbedShrink(embed);

        if (color != null) {
            builder.setColor(color);
        }

        if (title != null) {
            builder.setTitle(parse(title.replace(("{title}"), Objects.requireNonNull(embed.getTitle().get())), embed, message));
        }

        if (desc != null) {
            builder.setDescription(parse(desc.replace(("{description}"), Objects.requireNonNull(embed.getDescription().get())), embed, message));
        }

        message.clearEmbeds();
        message.embed(builder);
        message.send();

        return null;
    }

    public static String parse(String arg, EmbedShrink embed, IMessageBuilder message) {
        long timestamp = message.getTimeCreated();
        long diff = System.currentTimeMillis() - timestamp;
        arg = arg.replace("{timediff}", TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
        return arg;
    }

    @Command(desc = "Return the discord invite link for the bot", viewable = true)
    public String invite() {
        return "<https://discord.com/api/oauth2/authorize?client_id=" + Settings.INSTANCE.APPLICATION_ID + "&permissions=395606879321&scope=bot>\n" +
                "<https://github.com/xdnw/locutus/wiki>";
    }

    @Command(desc = "Unregister a nation to a discord user")
    public String unregister(@Me IMessageIO io, @Me JSONObject command, @Me User user, @Default("%user%") DBNation nation, @Switch("f") boolean force) {
        DBNation originalNation = nation;
        if (nation == null) nation = DiscordUtil.getNation(user);
        Long nationUserId = nation == null ? user.getIdLong() : nation.getUserId();
        DBNation userNation = DiscordUtil.getNation(user);
        if (force && !Roles.INTERNAL_AFFAIRS.hasOnRoot(user)) return "You do not have permission to force un-register.";
        if (originalNation != null && (userNation == null || !userNation.equals(originalNation)) && !force) {
            String title = "Unregister another user.";
            String body = nation.getNationUrlMarkup() + " | " + "<@" + nationUserId + ">";
            io.create().confirmation(title, body, command).send();
            return null;
        }
        if (nation != null) {
            Locutus.imp().getDiscordDB().deleteApiKeyPairByNation(nation.getNation_id());
            Locutus.imp().getDiscordDB().unregister(nation.getNation_id(), null);
        } else {
            Locutus.imp().getDiscordDB().unregister(null, user.getIdLong());
        }
        return "Unregistered user from " + nation.getUrl();
    }

    @Command(desc = "Register your discord user with your Politics And War nation.")
    public String register(@Me GuildDB db, @Me User user, /* @Default("%user%")  */ DBNation nation) throws IOException {
        boolean notRegistered = DiscordUtil.getUserByNationId(nation.getNation_id()) == null;
        String fullDiscriminator = DiscordUtil.getFullUsername(user);

        String errorMsg = "1. Go to: <" + Settings.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your discord username `" + fullDiscriminator + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command " + CM.register.cmd.nation(nation.getNation_id() + "").toSlashCommand() + " again";

        long id = user.getIdLong();
        boolean checkId = false;

        PNWUser existingUser = Locutus.imp().getDiscordDB().getUser(null, user.getName(), fullDiscriminator);

        /*
        Using register
         - If the discord user/discriminator is registered to another nation, require using the discord id
         - If the nation is registered to another user, require using the discord id
         (have message that they can change the discord setting afterwards to their username)
         */

        String discordIdErrorMsg = "That nation is already registered to another user!" +
                "1. Go to: <" + Settings.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your **DISCORD ID** `" + user.getIdLong() + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command " + CM.register.cmd.nation(nation.getNation_id() + "").toSlashCommand() + " again";

        if (existingUser != null && existingUser.getNationId() != nation.getNation_id()) {
            if (existingUser.getDiscordId() != id) {
                errorMsg = discordIdErrorMsg;
                checkId = fullDiscriminator.contains("#");
            }
        }
        Long existingUserId = nation.getUserId();
        if (existingUserId != null && existingUserId != id) {
            errorMsg = discordIdErrorMsg;
            checkId = fullDiscriminator.contains("#");
        }
        try {
            String pnwDiscordName = nation.fetchUsername();
            if (pnwDiscordName == null || pnwDiscordName.isEmpty()) {
                return errorMsg;
            }
            String userName = DiscordUtil.getFullUsername(user);
            if (checkId) {
                userName = "" + user.getIdLong();
            }
            if (!userName.equalsIgnoreCase(pnwDiscordName) && !pnwDiscordName.contains("" + user.getIdLong())) {
                return "Your user doesn't match: `" + pnwDiscordName + "` != `" + userName + "`\n\n" + errorMsg;
            }

            if (existingUser != null) {
                Locutus.imp().getDiscordDB().unregister(existingUser.getNationId(), existingUser.getDiscordId());
            }
            if (existingUserId != null) {
                Locutus.imp().getDiscordDB().unregister(nation.getNation_id(), existingUserId);
            }

            PNWUser pnwUser = new PNWUser(nation.getNation_id(), id, userName);
            Locutus.imp().getDiscordDB().addUser(pnwUser);
            return nation.register(user, db, notRegistered);
        } catch (InsufficientPermissionException e) {
            return e.getMessage();
        } catch (Throwable e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    @Command(desc = "Lists the shared servers where a user has a role.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String hasRole(User user, Roles role) {
        StringBuilder response = new StringBuilder();
        for (Guild other : user.getMutualGuilds()) {
            if (role.has(user, other)) {
                response.append(user.getName()).append(" has ").append(role.name()).append(" on ").append(other).append("\n");
            }
        }
        return response.toString();
    }

    @Command(desc = "Move a discord channel up 1 position")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelUp(@Me TextChannel channel) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() - 1));
        return null;
    }

    @Command(desc = "Delete a discord channel")
    @RolePermission(value = Roles.ADMIN)
    public String deleteChannel(@Me Guild guild, @Me User user, @Me Member member, MessageChannel channel) {
        GuildMessageChannel text = (GuildMessageChannel) channel;
        String[] split = text.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(user, guild)) && text.canTalk(member)) {
            RateLimitUtil.queue(text.delete());
            return null;
        } else {
            return "You do not have permission to close that channel.";
        }

    }

    @Command(desc = "Move a discord channel down 1 position")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelDown(@Me TextChannel channel) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() + 1));
        return null;
    }

    @Command(desc = "Send a message to the interview channels of the nations specified")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String interviewMessage(@Me GuildDB db, Set<DBNation> nations, String message, @Switch("p") boolean pingMentee) {
        Map<DBNation, IAChannel> map = db.getIACategory().getChannelMap();
        int num = 0;
        for (DBNation nation : nations) {
            IAChannel iaChan = map.get(nation);
            if (iaChan == null) continue;
            GuildMessageChannel channel = iaChan.getChannel();
            if (channel != null) {
                try {
                    String localMessage = message;
                    User user = nation.getUser();
                    if (pingMentee && user != null) {
                        localMessage += "\n" + user.getAsMention();
                    }
                    RateLimitUtil.queue(channel.sendMessage(localMessage));
                    num++;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        return "Done. Sent " + num + " messaged!";
    }

    @Command(desc = "Set the category for a discord channel")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelCategory(@Me Member member, @Me TextChannel channel, Category category) {
        if (channel.getParentCategory() != null && channel.getParentCategory().getIdLong() == category.getIdLong()) {
            return "Channel is already in category: " + category;
        }
        String[] split = channel.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(member)) && channel.canTalk(member)) {
            RateLimitUtil.queue(channel.getManager().setParent(category));
            return null;
        } else {
            return "You do not have permission to move that channel.";
        }
    }

    @Command(desc = "Create a discord modal for a bot command\n" +
            "This will make a popup prompting for the command arguments you specify and submit any defaults you provide\n" +
            "Note: This is intended to be used in conjuction with the card command")
    public String modal(@Me IMessageIO io, ICommand command,
                        @Arg("A comma separated list of the command arguments to prompt for") String arguments,
                        @Arg("The default arguments and values you want to submit to the command\n" +
                                "Example: `myarg1:myvalue1 myarg2:myvalue2`")
                        @Default String defaults) {
        Map<String, String> args;
        if (defaults == null) {
            args = new HashMap<>();
        } else if (defaults.startsWith("{") && defaults.endsWith("}")) {
            args = PW.parseMap(defaults);
        } else {
            args = CommandManager2.parseArguments(command.getUserParameterMap().keySet(), defaults, true);
        }
        io.modal().create(command, args, StringMan.split(arguments, ',')).send();
        return null;
    }

    @Command(desc = "Get the text from a discord image\n" +
            "It is recommended to crop the image first", viewable = true)
    public String ocr(String discordImageUrl) {
        if (!ImageUtil.isDiscordImage(discordImageUrl)) {
            throw new IllegalArgumentException("Invalid discord image url: `" + discordImageUrl + "`");
        }
        String text = ImageUtil.getText(discordImageUrl);
        return "```\n" +text + "\n```\n";
    }

    @Command(desc = "Shift the transfer note category flows for a nation.\n" +
            "For adjusting whether amounts are internal, withdrawn or deposited.\n" +
            "Does not change overall or note balance unless it is shifted to `#ignore`")
    @RolePermission(value = Roles.ECON)
    public String shiftFlow(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command, @Me User author,
                            DBNation nation,
                            DepositType noteFrom,
                            FlowType flowType,
                            Map<ResourceType, Double> amount,
                            @Default DepositType noteTo,
                            @Switch("a") DBAlliance alliance,
                            @Switch("f") boolean force) {
        if (noteTo == null) noteTo = DepositType.DEPOSIT;
        if (noteFrom == noteTo) {
            return "Cannot shift flow from `" + noteFrom.name() + "` to `" + noteTo.name() + "` please specify another `noteTo`";
        }
        long date = System.currentTimeMillis();
        String fromStr = "#" + noteFrom.name().toLowerCase(Locale.ROOT);
        String toStr = "#" + noteTo.name().toLowerCase(Locale.ROOT);
        long fromId;
        int fromType;
        String fromUrl;
        Set<Integer> ids = db.getAllianceIds();
        if (alliance != null && !ids.contains(alliance.getId())) {
            throw new IllegalArgumentException("Alliance " + alliance.getName() + " is not registered to this guild: " + CM.settings_default.registerAlliance.cmd.toSlashMention());
        }
        if (force) {
            Long allowedAllianceId = Roles.ECON.hasAlliance(author, db.getGuild());
            if (allowedAllianceId == null) {
                throw new IllegalArgumentException("Missing " + Roles.ECON.toDiscordRoleNameElseInstructions(db.getGuild()));
            }
            if (allowedAllianceId != 0L && allowedAllianceId != nation.getAlliance_id()) {
                throw new IllegalArgumentException("You can only shift deposit flow for nations in your alliance (" + PW.getMarkdownUrl(allowedAllianceId.intValue(), true));
            }
        }

        if (ids.isEmpty()) {
            fromId = db.getIdLong();
            fromUrl = DiscordUtil.getGuildName(db.getIdLong());
            fromType = db.getReceiverType();
        } else {
            fromType = 2;
            if (alliance != null) {
                fromId = alliance.getId();
            } else if (ids.contains(nation.getAlliance_id())) {
                fromId = nation.getAlliance_id();
            } else {
                fromId = ids.iterator().next();
            }
            fromUrl = PW.getMarkdownUrl((int) fromId, true);
        }

        double[] amtNeg = ResourceType.builder().subtract(amount).build();
        double[] amtPos = ResourceType.builder().add(amount).build();

        List<Runnable> tasks = new ArrayList<>();
        List<String> messages = new ArrayList<>();


        switch (flowType) {
            case INTERNAL -> {
                tasks.add(() -> db.addBalance(date, nation, me.getId(), fromStr, amtPos));
                tasks.add(() -> db.addBalance(date, nation, me.getId(), toStr, amtNeg));
                messages.add(flowType + " transfer " + ResourceType.toString(amtNeg) + " note: `" + fromStr + "`");
                messages.add(flowType + " transfer " + ResourceType.toString(amtPos) + " note: `" + toStr + "`");
            }
            case WITHDRAWAL -> {
                tasks.add(() -> db.addTransfer(date, fromId, fromType, nation, me.getId(), fromStr, amtPos));
                tasks.add(() -> db.addTransfer(date, fromId, fromType, nation, me.getId(), toStr, amtNeg));
                messages.add(flowType + " transfer " + ResourceType.toString(amtNeg) + " note: `" + fromStr + "` sender: " + fromUrl);
                messages.add(flowType + " transfer " + ResourceType.toString(amtPos) + " note: `" + toStr + "` sender: " + fromUrl);
            }
            case DEPOSIT -> {
                tasks.add(() -> db.addTransfer(date, nation, fromId, fromType, me.getId(), fromStr, amtPos));
                tasks.add(() -> db.addTransfer(date, nation, fromId, fromType, me.getId(), toStr, amtNeg));
                messages.add(flowType + " transfer " + ResourceType.toString(amtNeg) + " note: `" + fromStr + "` receiver: " + fromUrl);
                messages.add(flowType + " transfer " + ResourceType.toString(amtPos) + " note: `" + toStr + "` receiver: " + fromUrl);
            }
            default -> throw new IllegalArgumentException("Unexpected value: " + flowType);
        }

        if (!force) {
            String title = "Add Transfer Flow: " + nation.getName();
            StringBuilder body = new StringBuilder();
            body.append(nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup()).append("\n");
            body.append("Worth: `$" + MathMan.format(ResourceType.convertedTotal(amount)) + "`\n- ");
            body.append(StringMan.join(messages, "\n- "));
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }

        for (Runnable task : tasks) {
            task.run();
        }

        return "Done!\n- " + StringMan.join(messages, "\n- ");
    }

    @Command(desc = "Check the flow for a specific transaction note, showing the net by internal addbalance, withdrawals, and deposits")
    @RolePermission(value = Roles.ECON_STAFF)
    public String viewFlow(@Me GuildDB db, DBNation nation, DepositType note) {

        // public List<Map.Entry<Integer, Transaction2>> getTransactions(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff, boolean priority) {
        List<Map.Entry<Integer, Transaction2>> transfers = nation.getTransactions(db, null, false, true, 0, 0, true);

        if (note != null) {
            String noteStr = "#" + note.name().toLowerCase(Locale.ROOT);
            transfers.removeIf(f -> !PW.parseTransferHashNotes(f.getValue().note).containsKey(noteStr));
        }
        double[] manual = FlowType.INTERNAL.getTotal(transfers, nation.getNation_id());
//      - Amount withdrawn via a # note
        double[] withdrawn = FlowType.WITHDRAWAL.getTotal(transfers, nation.getNation_id());
//      - Amount deposit via a # note
        double[] deposited = FlowType.DEPOSIT.getTotal(transfers, nation.getNation_id());

        StringBuilder response = new StringBuilder();
        response.append("**" + FlowType.INTERNAL + "**: worth `$" + MathMan.format(ResourceType.convertedTotal(manual)) + "`\n");
        response.append("```json\n" + ResourceType.toString(manual) + "\n```\n");
//        response.append("Withrawal:\n```json\n" + ResourceType.toString(withdrawn) + "\n```\n");
        response.append("**" + FlowType.WITHDRAWAL + "**: worth `$" + MathMan.format(ResourceType.convertedTotal(withdrawn)) + "`\n");
        response.append("```json\n" + ResourceType.toString(withdrawn) + "\n```\n");
//        response.append("Deposits:\n```json\n" + ResourceType.toString(deposited) + "\n```\n");
        response.append("**" + FlowType.DEPOSIT + "**: worth `$" + MathMan.format(ResourceType.convertedTotal(deposited)) + "`\n");
        response.append("```json\n" + ResourceType.toString(deposited) + "\n```\n");
        return response.toString();
    }
}
