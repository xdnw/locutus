package com.boydti.discord.commands.manager.v2.impl.pw.commands;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Default;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.binding.annotation.RegisteredRole;
import com.boydti.discord.commands.manager.v2.binding.annotation.Switch;
import com.boydti.discord.commands.manager.v2.binding.annotation.TextArea;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.PNWUser;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.FileUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.offshore.test.IAChannel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class DiscordCommands {
    @Command
    public String say(@Me GuildDB db, @Me Guild guild, @Me MessageChannel channel, @Me User author, @Me DBNation me, @Me Message message, List<String> args) {
        String msg = DiscordUtil.trimContent(message.getContentRaw());
        msg = msg.replace("@", "@\u200B");
        msg = msg.replace("&", "&\u200B");
        msg = msg + "\n\n - " + author.getAsMention();
        return DiscordUtil.format( guild, channel, author, me, msg.substring(5));
    }

    @Command(desc = "Import all emojis from another guild", aliases = {"importEmoji", "importEmojis"})
    @RolePermission(Roles.ADMIN)
    public String importEmojis(@Me MessageChannel channel, Guild guild) {
        if (!Settings.INSTANCE.DISCORD.CACHE.EMOTE) {
            throw new IllegalStateException("Please enable DISCORD.CACHE.EMOTE in " + Settings.INSTANCE.getDefaultFile());
        }
        if (!Settings.INSTANCE.DISCORD.INTENTS.EMOJI) {
            throw new IllegalStateException("Please enable DISCORD.INTENTS.EMOJI in " + Settings.INSTANCE.getDefaultFile());
        }
        List<Emote> emotes = guild.getEmotes();

        for (Emote emote : emotes) {
            if (emote.isManaged() || !emote.isAvailable()) {
                continue;
            }

            String url = emote.getImageUrl();
            byte[] bytes = FileUtil.readBytesFromUrl(url);

            RateLimitUtil.queue(channel.sendMessage("Creating emote: " + emote.getName() + " | " + url));

            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                guild.createEmote(emote.getName(), icon).complete();
            }
        }
        return "Done!";
    }

    @Command(desc="Generate a card which runs a command when users react to it.\nPut commands inside \"quotes\".\n" +
            "Prefix a command with a #channel e.g. `\"#channel $embedcommand\"` to have the command output go there\n\n" +
            "Prefix the command with:" +
            "`~$command` to remove the user's reaction upon use and keep the card\n" +
            "`_$command` to remove ALL reactions upon use and keep the card\n" +
            "`.$command` to keep the card upon use\n\n" +
            "Example:\n" +
            "`$embed 'Some Title' 'My First Embed' '~$embedsay Hello {nation}' '$embedsay Goodbye {nation}'`",
    aliases = {"card", "embed"})
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String card(@Me MessageChannel channel, String title, String body, @TextArea List<String> commands) {
        String emoji = "\ufe0f\u20e3";

        if (commands.size() > 10) {
            return "Too many commands (max: 10, provided: " + commands.size() + ")\n" +
                    "Note: Commands must be inside \"double quotes\", and each subsequent command separated by a space";
        }

        ArrayList<String> reactions = new ArrayList<String>();
        for (int i = 0; i < commands.size(); i++) {
            String cmd = commands.get(i);
            String codePoint = i + emoji;
            reactions.add(codePoint);
            reactions.add(cmd);
        }
        String[] reactionsArr = reactions.toArray(new String[0]);
        Message msg = DiscordUtil.createEmbedCommand(channel, title, body, reactionsArr);
        return null;
    }

    @Command(desc = "Create a channel with name in a specified category and ping the specified roles upon creation")
    public String channel(@Me GuildDB db, @Me Message message, @Me User author, @Me Guild guild, @Me MessageChannel output, @Me DBNation nation,
                          String channelName, Category category, @Default String copypasta,
                          @Switch('i') boolean addIA,
                          @Switch('m') boolean addMilcom,
                          @Switch('f') boolean addFa,
                          @Switch('e') boolean addEa,
                          @Switch('p') boolean pingRoles,
                          @Switch('a') boolean pingAuthor

                          ) {
        channelName = DiscordUtil.format(guild, output, author, nation, channelName);

        Member member = guild.getMember(author);

        List<IPermissionHolder> holders = new ArrayList<>();
        holders.add(member);
        holders.addAll(member.getRoles());
        holders.add(guild.getRolesByName("@everyone", false).get(0));

        boolean hasOverride = message.getAuthor().getIdLong() == Settings.INSTANCE.APPLICATION_ID;
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
        if (addIA) roles.add(Roles.INTERNAL_AFFAIRS);
        if (addMilcom) roles.add(Roles.MILCOM);
        if (addFa) roles.add(Roles.FOREIGN_AFFAIRS);
        if (addEa) roles.add(Roles.ECON);
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
            if (copypasta != null && !copypasta.isEmpty()) {
                String key = "copypasta." + copypasta;
                String copyPasta = db.getInfo(key);
                if (copyPasta != null) {
                    com.boydti.discord.util.RateLimitUtil.queue(createdChannel.sendMessage(copyPasta));
                }
            }
            if (pingRoles) {
                for (Roles dept : roles) {
                    Role role = dept.toRole(guild);
                    if (role != null) {
                        createdChannel.sendMessage(role.getAsMention()).complete();
                    }
                }
            }
            if (pingAuthor) {
                com.boydti.discord.util.RateLimitUtil.queue(createdChannel.sendMessage(author.getAsMention()));
            }
        }

        return "Channel: " + createdChannel.getAsMention();
    }

    private TextChannel updateChannel(TextChannel channel, IPermissionHolder holder, Set<Roles> depts) {
        channel.putPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL).complete();
        channel.putPermissionOverride(holder).grant(Permission.VIEW_CHANNEL).complete();

        for (Roles dept : depts) {
            Role role = dept.toRole(channel.getGuild());
            if (role != null) {
                channel.putPermissionOverride(role).grant(Permission.VIEW_CHANNEL).complete();
            }
        }
        return channel;
    }

    @Command(desc = "Get info from a locutus embed")
    @RolePermission(value = Roles.ADMIN)
    public String embedInfo(Message message) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embed found";

        MessageEmbed embed = embeds.get(0);
        String title = embed.getTitle();
        String desc = embed.getDescription();
        Map<String, String> reactions = DiscordUtil.getReactions(embed);

        if (reactions == null) {
            return "No embed commands found";
        }

        String cmd = "!embed " + "\"" + title + "\" \"" + desc + "\" \"" + StringMan.join(reactions.values(), "\" \"") + "\"";
        return "```" + cmd + "```";
    }

    @Command
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String updateEmbed(@Me Guild guild, @Me User user, @Me Message message, @Switch('r') @RegisteredRole Roles requiredRole, @Switch('c') Color color, @Switch('t') String title, @Switch('d') String desc) {
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) return "This command can only be run when bound to a Locutus embed";
        if (requiredRole != null) {
            if (!requiredRole.has(user, guild)) {
                return null;
            }
        }

        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        MessageEmbed embed = embeds.get(0);

        EmbedBuilder builder = new EmbedBuilder(embed);

        if (color != null) {
            builder.setColor(color);
        }

        if (title != null) {
            builder.setTitle(parse(title.replace(("{title}"), embed.getTitle()), embed, message));
        }

        if (desc != null) {
            builder.setDescription(parse(desc.replace(("{description}"), embed.getDescription()), embed, message));
        }

        DiscordUtil.updateEmbed(builder, null, new Function<EmbedBuilder, Message>() {
            @Override
            public Message apply(EmbedBuilder builder) {
                 return com.boydti.discord.util.RateLimitUtil.complete(message.getChannel().editMessageEmbedsById(message.getIdLong(), builder.build()));
            }
        });

        return null;
    }

    private String parse(String arg, MessageEmbed embed, Message message) {
        long timestamp = message.getTimeCreated().toEpochSecond() * 1000L;
        long diff = System.currentTimeMillis() - timestamp;
        arg = arg.replace("{timediff}", TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
        return arg;
    }

    @Command
    public String invite() {
        return "<https://docs.google.com/document/d/1Qq6Qe7KtCy-Dlqktz8bhNfrUpcbf7oM8F6gRVNR28Dw/edit?usp=sharing>";
    }

    @Command(desc = "Unregister a nation to a discord user")
    public String unregister(@Me MessageChannel channel, @Me Message message, @Me User user, @Default("%user%") DBNation nation, @Switch('f') boolean force) {
        User nationUser = nation.getUser();
        if (nationUser == null) return "That nation is not registered";
        if (force && !Roles.ADMIN.hasOnRoot(user)) return "You do not have permission to force unregister";
        if (!user.equals(nationUser) && !force) {
            String title = "Unregister ANOTHER user";
            String body = nation.getNationUrlMarkup(true) + " | " + nationUser.getAsMention() + " | " + nationUser.getName();
            DiscordUtil.pending(channel, message, title, body + "\nPress to confirm", 'f');
            return null;
        }
        Locutus.imp().getDiscordDB().unregister(nation.getNation_id(), null);
        return "Unregistered " + nationUser.getAsMention() + " from " + nation.getNationUrl();
    }

    @Command(desc = "Register with your Politics And War nation")
    public String register(Guild guild, @Me User user, @Default("%user%") DBNation nation) throws IOException {
        boolean notRegistered = DiscordUtil.getUserByNationId(nation.getNation_id()) == null;
        String fullDiscriminator = user.getName() + "#" + user.getDiscriminator();

        String errorMsg = "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your discord username `" + fullDiscriminator + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command `!validate " + nation.getNation_id() + "` again";

        long id = user.getIdLong();
        boolean checkId = false;

        PNWUser existingUser = Locutus.imp().getDiscordDB().getUser(null, user.getName(), fullDiscriminator);
        if (existingUser != null) {
            if (existingUser.getDiscordId() == null && !existingUser.getDiscordId().equals(id)) {
                errorMsg = "That nation is already registered to another user!" +
                        "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                        "2. Scroll down to where it says Discord Username:\n" +
                        "3. Put your **DISCORD ID** `" + user.getIdLong() + "` in the field\n" +
                        "4. Click save\n" +
                        "5. Run the command `!validate " + nation.getNation_id() + "` again";
                checkId = true;
            }
        }
        try {
            String pnwDiscordName = nation.fetchUsername();
            if (pnwDiscordName == null || pnwDiscordName.isEmpty()) {
                return errorMsg;
            }
            String userName = user.getName() + "#" + user.getDiscriminator();
            if (checkId) {
                userName = "" + user.getIdLong();
            }
            if (!userName.equalsIgnoreCase(pnwDiscordName)) {
                return "Your user doesnt match: `" + pnwDiscordName + "` != `" + userName + "`\n\n" + errorMsg;
            }

            PNWUser pnwUser = new PNWUser(nation.getNation_id(), id, fullDiscriminator);
            Locutus.imp().getDiscordDB().addUser(pnwUser);
            return nation.register(user, guild, notRegistered);
        } catch (InsufficientPermissionException e) {
            return e.getMessage();
        } catch (Throwable e) {
            e.printStackTrace();
            return "Error (see console) <@" + Settings.INSTANCE.ADMIN_USER_ID + ">";
        }
    }


    @Command(desc = "Lists the shared servers where a user has a role")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String hasRole(User user, Roles role) {
        StringBuilder response = new StringBuilder();
        for (Guild other : user.getMutualGuilds()) {
            Role discRole = role.toRole(other);
            if (other.getMember(user).getRoles().contains(discRole)) {
                response.append(user.getName() + " has " + role.name() + " | @" + discRole.getName() + " on " + other + "\n");
            }
        }
        return response.toString();
    }

    @Command()
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelUp(@Me TextChannel channel, @Me Message message) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() - 1));
        com.boydti.discord.util.RateLimitUtil.queue(message.delete());
        return null;
    }

    @Command()
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String deleteChannel(@Me Guild guild, @Me User user, @Me Member member, MessageChannel channel) {
        GuildMessageChannel text = (GuildMessageChannel) channel;
        String[] split = text.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(user, guild)) && text.canTalk(member)) {
            com.boydti.discord.util.RateLimitUtil.queue(text.delete());
            return null;
        } else {
            return "You do not have permission to close that channel";
        }

    }

    @Command()
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelDown(@Me TextChannel channel, @Me Message message) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() + 1));
        com.boydti.discord.util.RateLimitUtil.queue(message.delete());
        return null;
    }

    @Command()
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String interviewMessage(@Me GuildDB db, Set<DBNation> nations, String message, @Switch('p') boolean ping) {
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
                    if (ping && user != null) {
                        localMessage += "\n" + user.getAsMention();
                    }
                    com.boydti.discord.util.RateLimitUtil.queue(channel.sendMessage(localMessage));
                    num++;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        return "Done. Sent " + num + " messaged!";
    }

    @Command()
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelCategory(@Me Guild guild, @Me Member member, @Me TextChannel channel, @Me Message message, Category category) {
        String[] split = channel.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(member)) && channel.canTalk(member)) {
            com.boydti.discord.util.RateLimitUtil.queue(channel.getManager().setParent(category));
            com.boydti.discord.util.RateLimitUtil.queue(message.delete());
            return "Done";
        } else {
            return "You do not have permission to move that channel";
        }
    }

    @Command(desc = "Modify the permissions for a list of nations in a channel")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public static String channelPermissions(@Me Member author, @Me Guild guild, TextChannel channel, Set<DBNation> nations, Permission permission, @Switch('n') boolean negate, @Switch('r') boolean removeOthers, @Switch('l') boolean listChanges, @Switch('p') boolean pingAddedUsers) {
        if (!author.hasPermission(channel, Permission.MANAGE_PERMISSIONS)) throw new IllegalArgumentException("You do not have " + Permission.MANAGE_PERMISSIONS + " in " + channel.getAsMention());

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
                continue;
            } else if (!contains && isSet && removeOthers) {
                toRemove.add(member);
            }
        }
        Function<Member, String> nameFuc = Member::getEffectiveName;
        if (pingAddedUsers) {
            listChanges = true;
            nameFuc = IMentionable::getAsMention;
        }

        for (Member member : members) {
            PermissionOverrideAction override = channel.createPermissionOverride(member);
            PermissionOverrideAction action;
            if (negate) {
                action = override.deny(permission);
            } else {
                action = override.grant(permission);
            }
            action.complete();

            changes.add("Set " + permission + "=" + !negate + " for " + nameFuc.apply(member));
        }

        for (Member member : toRemove) {
            channel.putPermissionOverride(member).clear(permission).complete();
            changes.add("Clear " + permission + " for " + nameFuc.apply(member));
        }

        StringBuilder response = new StringBuilder("Done!");
        if (listChanges && !changes.isEmpty()) {
            response.append("\n - " + StringMan.join(changes, "\n - "));
        }
        return response.toString();
    }
}
