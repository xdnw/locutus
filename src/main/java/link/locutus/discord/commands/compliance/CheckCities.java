package link.locutus.discord.commands.compliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.task.ia.IACheckup;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class CheckCities extends Command {
    public CheckCities() {
        super("checkup", "check-cities", "checkcities", "Normalmaaudit", CommandCategory.INTERNAL_AFFAIRS, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return super.help() + " <nation|*>";
    }

    @Override
    public String desc() {
        return "Check a nations cities for compliance.\n" +
                "Use `-p` to ping\n" +
                "Use `-m` to mail results\n" +
                "Use `-c` to post results in `interview` channels";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    private Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> auditResults = new HashMap<>();

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        Integer page = DiscordUtil.parseArgInt(args, "page");
        if (args.size() != 1) {
            return usage(event);
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Set<Integer> aaIds = db.getAllianceIds(true);
        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.INTERNAL_AFFAIRS_STAFF.has(event.getAuthor(), event.getGuild())) return "No permission to use *";

            nations = Locutus.imp().getNationDB().getNations(aaIds);
            nations.removeIf(n -> n.getPosition() <= 1 ||
                    n.getVm_turns() > 0 ||
                    n.getActive_m() > 10000
                    );
        } else {
            nations = DiscordUtil.parseNations(event.getGuild(), args.get(0));
        }

        if (aaIds.isEmpty()) return "No alliance registered to this guild";

        if (nations.isEmpty()) {
            return "No nations found for: `" + args.get(0) + "`" + ". Have they used " + CM.register.cmd.toSlashMention() + " ?";
        }
        for (DBNation nation : nations) {
            if (!db.isAllianceId(nation.getAlliance_id())) {
                return "Nation `" + nation.getName() + "` is in " + nation.getAlliance().getQualifiedName() + " but this server is registered to: "
                        + StringMan.getString(db.getAllianceIds()) + "\nSee: " + CM.settings.cmd.toSlashMention() + " with key `" + GuildDB.Key.ALLIANCE_ID + "`";
            }
            if (!aaIds.contains(nation.getAlliance_id())) {
                return "Nation is not in the same alliance: " + nation.getNation() + " != " + StringMan.getString(aaIds);
            }
        }

        if (nations.size() > 1) {
            IACategory category = db.getIACategory();
            if (category != null) {
                category.load();
                category.purgeUnusedChannels(new DiscordChannelIO(event.getChannel()));
                category.alertInvalidChannels(new DiscordChannelIO(event.getChannel()));
            }
        }

        me.setMeta(NationMeta.INTERVIEW_CHECKUP, (byte) 1);

        StringBuilder output = new StringBuilder();

        boolean individual = flags.contains('f') || nations.size() == 1;
        IACheckup checkup = new IACheckup(db, db.getAllianceList().subList(aaIds), false);

        boolean mail = flags.contains('m');
        ApiKeyPool keys = mail ? db.getMailKey() : null;
        if (mail && keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + CM.credentials.addApiKey.cmd.toSlashMention() + "");

        for (DBNation nation : nations) {
            int failed = 0;
            boolean appendNation = false;

            Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult = null;
            if (page != null) {
                auditResult = auditResults.get(nation);
            }
            if (auditResult == null) {
                CompletableFuture<Message> messageFuture = event.getChannel().sendMessage("Fetching city info: (this will take a minute)").submit();
                auditResult = checkup.checkup(nation, individual, false);
                auditResults.put(nation, auditResult);
                RateLimitUtil.queue(messageFuture.get().delete());
            }
            if (auditResult != null) {
                auditResult = IACheckup.simplify(auditResult);
            }

            if (!auditResult.isEmpty()) {
                for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : auditResult.entrySet()) {
                    IACheckup.AuditType type = entry.getKey();
                    Map.Entry<Object, String> info = entry.getValue();
                    if (info == null || info.getValue() == null) continue;
                    failed++;

                    output.append("**").append(type.toString()).append(":** ");
                    output.append(info.getValue()).append("\n\n");
                }
            }
            if (failed > 0) {
                createEmbed(nation, event, failed, auditResult, page);

                if (flags.contains('p')) {
                    PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getNation_id());
                    if (user != null) {
                        event.getChannel().sendMessage("^ " + user.getAsMention()).complete();
                    }
                } else if (mail) {
                    String title = nation.getAllianceName() + " automatic checkup";

                    String input = output.toString().replace("_", " ").replace(" * ", " STARPLACEHOLDER ");
                    String markdown = MarkupUtil.markdownToHTML(input);
                    markdown = MarkupUtil.transformURLIntoLinks(markdown);
                    markdown = MarkupUtil.htmlUrl(nation.getName(), nation.getNationUrl()) + "\n" + markdown;
                    markdown += ("\n\nPlease get in contact with us via discord for assistance");
                    markdown = markdown.replace("\n", "<br>").replace(" STARPLACEHOLDER ", " * ");

                    JsonObject response = nation.sendMail(keys, title, markdown);
                    String userStr = nation.getNation() + "/" + nation.getNation_id();
                    RateLimitUtil.queue(event.getChannel().sendMessage(userStr + ": " + response));
                }
            } else {
                event.getChannel().sendMessage("All checks passed for " + nation.getNation()).complete();
            }
            output.setLength(0);
        }

        if (flags.contains('c')) {
            if (db.getGuild().getCategoriesByName("interview", true).isEmpty()) {
                return "No `interview` category";
            }

            IACategory category = db.getIACategory();
            if (category.isValid()) {
                category.update(auditResults);
            }
        }

        return null;
    }

    private void createEmbed(DBNation nation, MessageReceivedEvent event, int failed, Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult, Integer page) {
        IACheckup.createEmbed(event.getChannel(), event.getMessage(), Settings.commandPrefix(true) + "Checkup " + nation.getNation_id(), nation, auditResult, page);
    }
}
