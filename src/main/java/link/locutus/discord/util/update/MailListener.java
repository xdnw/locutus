package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.GPTBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NewsletterBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.commands.war.Spyops;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.mail.MailReceivedEvent;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.MailRespondTask;
import link.locutus.discord.util.task.mail.AlertMailTask;
import link.locutus.discord.util.task.mail.Mail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import retrofit2.http.HEAD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MailListener {
    private final CommandGroup commands;
    private final ValueStore store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;

    public MailListener(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        this.commands = CommandGroup.createRoot(store, validators);
        this.store = store;
        this.validators = validators;
        this.permisser = permisser;

        this.commands.registerCommands(this);
    }

    @Subscribe
    public void onMailReceived(MailReceivedEvent event) throws IOException {
        GuildMessageChannel channel = event.getChannel();
        if (channel == null) {
            new Exception().printStackTrace();
            System.out.println("No channel found for mail " + event.getAuth().getNationId() + " | " + event.getDefaultChannelId());
            return;
        }
        String body = event.toEmbedString();
        DiscordChannelIO outputBuilder = new DiscordChannelIO(channel);
        IMessageBuilder builder = outputBuilder.create();

        Guild guild = channel.getGuild();
        Role role = Roles.MAIL.toRole(guild);
        if (role != null) {
            builder.append("^ " + role.getAsMention());
        }
        int authId = event.getAuth().getNationId();
        DBNation receiver = Locutus.imp().getNationDB().getNationByLeader(event.getMail().leader);
        if (receiver.getId() == authId) {
            body += "\n\nUse " + CM.mail.reply.cmd.toSlashMention() + " (with `sender:" + authId + "` and then the recipient) to reply to this message.";
        }
        builder.embed(event.getTitle(), body.toString());
        if (receiver.getId() != authId) {
            CM.mail.reply mailCmd = CM.mail.reply.cmd.receiver(receiver.getId() + "").url(event.getUrl()).message("").sender(event.getAuth().getNationId() + "");
            builder.modal(CommandBehavior.DELETE_PRESSED_BUTTON, mailCmd, "\uD83D\uDCE7 Reply");
        }

        builder.commandButton(CommandBehavior.UNPRESS, CM.mail.read.cmd.messageId(event.getMail().id + "").account(authId + ""), "Read");
        builder.send();

        processCommands(Locutus.imp().getGuildDB(guild), guild, outputBuilder, event);
    }

    private LocalValueStore createLocals(GuildDB db, Guild guild, IMessageIO io, MailReceivedEvent event, String input) {
        LocalValueStore locals = new LocalValueStore<>(store);
        locals.addProvider(Key.of(GuildDB.class, Me.class), db);
        locals.addProvider(Key.of(Guild.class, Me.class), guild);
        locals.addProvider(Key.of(DBNation.class, Me.class), event.getNation());
        locals.addProvider(Key.of(MailReceivedEvent.class), event);
        locals.addProvider(Key.of(Mail.class), event.getMail());

        return locals;
    }

    private void processCommands(GuildDB db, Guild guild, IMessageIO io, MailReceivedEvent event) throws IOException {
        try {
            DBNation nation = event.getNation();
            List<String> messages = event.getMessages();

            if (nation == null || messages.isEmpty()) return;

            Mail mail = event.getMail();

            String subject = mail.subject;
            String msg = messages.get(0).trim();
            if (msg.isEmpty()) return;

            StringBuilder remaining = new StringBuilder();
            CommandCallable callable = commands.getCallable(msg, remaining);
            if (callable == null) {
                System.out.println("No command found for: `" + msg + "`");
                return;
            }

            List<String> args = remaining.isEmpty() ? new ArrayList<>() : StringMan.split(remaining.toString(), " ");
            System.out.println("Running mail command `" + msg + "`");
            LocalValueStore locals = createLocals(db, guild, io, event, msg);
            ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
            Object response = callable.call(stack);
            if (response != null) {
                io.sendMessage(response.toString());
                String html = MarkupUtil.markdownToHTML(response.toString());
                try {
                    event.reply(html);
                } catch (Throwable e) {
                    AlertUtil.error(e.getMessage(), e);
                    e.printStackTrace();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Command(desc = "Get more spy targets")
    public String more(@Me GuildDB db, @Me Guild guild, @Me DBNation nation, MailReceivedEvent event, Mail mail, @Default Set<MilitaryUnit> unitTypes) throws IOException {
        String targets;
        if (!db.getCoalition(Coalition.ENEMIES).isEmpty()) {
            Spyops cmd = new Spyops();

            String type = unitTypes == null ? "*" : StringMan.join(unitTypes, ",");
            ArrayList<String> args = new ArrayList<>(Arrays.asList("#wars>0,enemies", type));
            Set<Character> flags = new HashSet<>(Arrays.asList('s', 'r'));
            targets = cmd.run(null, nation.getUser(), nation, nation, db, args, flags);
        } else if (db == null) {
            targets = "Your guild " + guild + " does not have this Bot setup. Use the command on discord instead:\n" + CM.spy.find.target.cmd.toSlashMention() +  "";
        } else {
            targets = "Your guild " + guild + " does not have any enemies set. Use the command on discord instead:\n" + CM.spy.find.target.cmd.toSlashMention() + "";
        }
        if (targets != null) {
            String reply = MarkupUtil.bbcodeToHTML(targets);
            String response = event.reply(reply);
            return "Sending target messages to " + nation.getNation() + ": " + response;
        } else {
            return "No targets found";
        }
    }
}
