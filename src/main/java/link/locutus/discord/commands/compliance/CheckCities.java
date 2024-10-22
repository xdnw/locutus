package link.locutus.discord.commands.compliance;

import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CheckCities extends Command {
    private final Map<DBNation, Map<IACheckup.AuditType, Map.Entry<Object, String>>> auditResults = new HashMap<>();

    public CheckCities() {
        super("checkup", "check-cities", "checkcities", "Normalmaaudit", CommandCategory.INTERNAL_AFFAIRS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.audit.run.cmd);
    }
    @Override
    public String help() {
        return super.help() + " <nation|*>";
    }

    @Override
    public String desc() {
        return """
                Check a nations cities for compliance.
                Use `-p` to ping.
                Use `-m` to mail results.
                Use `-c` to post results in `interview` channels.""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Integer page = DiscordUtil.parseArgInt(args, "page");
        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Set<Integer> aaIds = db.getAllianceIds(true);
        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) return "No permission.";

            nations = Locutus.imp().getNationDB().getNationsByAlliance(aaIds);
            nations.removeIf(n -> n.getPosition() <= 1 ||
                    n.getVm_turns() > 0 ||
                    n.active_m() > 10000
                    );
        } else {
            nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
        }

        if (aaIds.isEmpty()) return "No alliance registered to this guild";

        if (nations.isEmpty()) {
            return "No nations found for: `" + args.get(0) + "`" + ". Have they used " + CM.register.cmd.toSlashMention() + " ?";
        }
        for (DBNation nation : nations) {
            if (!db.isAllianceId(nation.getAlliance_id())) {
                return "Nation `" + nation.getName() + "` is in " + nation.getAlliance().getQualifiedId() + " but this server is registered to: "
                        + StringMan.getString(db.getAllianceIds()) + "\nSee: " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.ALLIANCE_ID.name() + "`";
            }
            if (!aaIds.contains(nation.getAlliance_id())) {
                return "Nation is not in the same alliance: " + nation.getNation() + " != " + StringMan.getString(aaIds);
            }
        }

        if (nations.size() > 1) {
            IACategory category = db.getIACategory();
            if (category != null) {
                category.load();
                category.purgeUnusedChannels(channel);
                category.alertInvalidChannels(channel);
            }
        }

        me.setMeta(NationMeta.INTERVIEW_CHECKUP, (byte) 1);

        StringBuilder output = new StringBuilder();

        boolean individual = flags.contains('f') || nations.size() == 1;
        AllianceList aaList = db.getAllianceList();
        if (aaList == null) {
            return "Guild is not registered to an alliance. See: " + GuildKey.ALLIANCE_ID.getCommandMention();
        }
        IACheckup checkup = new IACheckup(db, aaList.subList(aaIds), false);

        boolean mail = flags.contains('m');
        ApiKeyPool keys = mail ? db.getMailKey() : null;
        if (mail && keys == null)
            throw new IllegalArgumentException("No API_KEY set, please use " + GuildKey.API_KEY.getCommandMention() + "");

        for (DBNation nation : nations) {
            int failed = 0;

            Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult = null;
            if (page != null) {
                auditResult = auditResults.get(nation);
            }
            if (auditResult == null) {
                CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Fetching city info: (this will take a minute)");
                auditResult = checkup.checkup(nation, individual, false);
                auditResults.put(nation, auditResult);
                try {
                    IMessageBuilder msg = msgFuture.get();
                    if (msg != null && msg.getId() > 0) {
                        channel.delete(msg.getId());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            if (auditResult != null) {
                auditResult = IACheckup.simplify(auditResult);
            }

            assert auditResult != null;
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
                createEmbed(channel, nation, auditResult, page);

                if (flags.contains('p')) {
                    PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getNation_id());
                    if (user != null) {
                        channel.sendMessage("^ " + user.getAsMention());
                    }
                } else if (mail) {
                    String title = nation.getAllianceName() + " Automatic checkup";

                    String input = output.toString().replace("_", " ").replace(" * ", " STARPLACEHOLDER ");
                    String markdown = MarkupUtil.markdownToHTML(input);
                    markdown = MarkupUtil.transformURLIntoLinks(markdown);
                    markdown = MarkupUtil.htmlUrl(nation.getName(), nation.getUrl()) + "\n" + markdown;
                    markdown += ("\n\nPlease get in contact with us via discord for assistance.");
                    markdown = markdown.replace("\n", "<br>").replace(" STARPLACEHOLDER ", " * ");

                    JsonObject response = nation.sendMail(keys, title, markdown, false);
                    String userStr = nation.getNation() + "/" + nation.getNation_id();
                    channel.sendMessage(userStr + ": " + response);
                }
            } else {
                channel.sendMessage("All checks passed for " + nation.getNation());
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

    private void createEmbed(IMessageIO channel, DBNation nation, Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult, Integer page) {
        IACheckup.createEmbed(channel, Settings.commandPrefix(true) + "Checkup " + nation.getNation_id(), nation, auditResult, page);
    }
}
